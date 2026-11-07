uniffi::setup_scaffolding!();

mod catalog;
mod detail;
mod http;

use regex::Regex;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

pub use catalog::Card;
pub use detail::{Episode, MovieDetail, Quality, Season, StreamSet, Translator};

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("Сетевая ошибка: {msg}")]
    Network { msg: String },
    #[error("Ошибка разбора: {msg}")]
    Parse { msg: String },
    #[error("Ошибка входа: {msg}")]
    Auth { msg: String },
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct Profile {
    pub logged_in: bool,
    pub email: String,
    pub user_id: String,
}

#[derive(uniffi::Object)]
pub struct Client {
    http: http::Http,
}

#[uniffi::export]
impl Client {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            http: http::Http::new(),
        })
    }

    pub fn login(&self, email: String, password: String) -> Result<Profile, CoreError> {
        let url = format!("{}/ajax/login/", http::BASE);
        let referer = format!("{}/", http::BASE);
        let resp = self.http.post_form(
            &url,
            &[
                ("login_name", email),
                ("login_password", password),
                ("login_not_save", "0".to_string()),
            ],
            Some(&referer),
        )?;
        let value: serde_json::Value = resp.json().map_err(|e| CoreError::Parse {
            msg: format!("ответ входа не JSON: {e}"),
        })?;
        if value
            .get("success")
            .and_then(|v| v.as_bool())
            .unwrap_or(false)
        {
            self.profile()
        } else {
            let msg = value
                .get("message")
                .and_then(|v| v.as_str())
                .unwrap_or("Неверный email или пароль")
                .to_string();
            Err(CoreError::Auth { msg })
        }
    }

    pub fn profile(&self) -> Result<Profile, CoreError> {
        let html = self.http.get_html(&format!("{}/", http::BASE))?;
        if html.contains("id=\"loginForm\"") {
            return Ok(Profile {
                logged_in: false,
                email: String::new(),
                user_id: String::new(),
            });
        }
        let re = Regex::new(r"profileId:\s*'(\d+)',\s*email:\s*'([^']*)'").unwrap();
        Ok(match re.captures(&html) {
            Some(c) => Profile {
                logged_in: true,
                email: c[2].to_string(),
                user_id: c[1].to_string(),
            },
            None => Profile {
                logged_in: false,
                email: String::new(),
                user_id: String::new(),
            },
        })
    }

    pub fn logout(&self) {
        self.http.clear_cookies();
    }

    pub fn export_cookies(&self) -> String {
        self.http.export_cookies()
    }

    pub fn import_cookies(&self, cookies: String) {
        self.http.import_cookies(&cookies);
    }

    pub fn homepage(&self) -> Result<Vec<Card>, CoreError> {
        let url = format!("{}/", http::BASE);
        let html = self.http.get_html(&url)?;
        guard_login(&html)?;
        Ok(catalog::parse_homepage_series(&html, 12))
    }

    pub fn category(&self, kind: String) -> Result<Vec<Card>, CoreError> {
        let section = match kind.as_str() {
            "films" | "series" | "cartoons" => kind,
            _ => "films".to_string(),
        };
        self.load_cards(&format!("{}/{}/", http::BASE, section))
    }

    pub fn search(&self, query: String) -> Result<Vec<Card>, CoreError> {
        if query.trim().is_empty() {
            return Ok(Vec::new());
        }
        let url = format!(
            "{}/search/?do=search&subaction=search&q={}",
            http::BASE,
            urlencoding::encode(query.trim())
        );
        self.load_cards(&url)
    }

    pub fn open(&self, path: String) -> Result<MovieDetail, CoreError> {
        let url = catalog::normalize_path(&path);
        let html = self.http.get_html(&url)?;
        guard_login(&html)?;
        detail::parse_detail(&html, &url)
    }

    pub fn streams(
        &self,
        detail: MovieDetail,
        translator_id: String,
        season_id: String,
        episode_id: String,
    ) -> Result<StreamSet, CoreError> {
        let raw = if detail.is_series {
            self.series_streams(&detail, &translator_id, &season_id, &episode_id)?
        } else {
            detail.streams_raw.clone()
        };

        let qualities = detail::parse_streams(&raw);
        if qualities.is_empty() {
            return Err(CoreError::Parse {
                msg: "не удалось найти ссылки на видео (возможно, нужен Premium)".to_string(),
            });
        }
        Ok(StreamSet { qualities })
    }
}

impl Client {
    fn load_cards(&self, url: &str) -> Result<Vec<Card>, CoreError> {
        let html = self.http.get_html(url)?;
        guard_login(&html)?;
        Ok(catalog::parse_cards(&html))
    }

    fn series_streams(
        &self,
        detail: &MovieDetail,
        translator_id: &str,
        season_id: &str,
        episode_id: &str,
    ) -> Result<String, CoreError> {
        let stamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_millis())
            .unwrap_or(0);
        let url = format!("{}/ajax/get_cdn_series/?t={}", http::BASE, stamp);
        let referer = catalog::normalize_path(&detail.path);
        let resp = self.http.post_form(
            &url,
            &[
                ("id", detail.id.clone()),
                ("translator_id", translator_id.to_string()),
                ("season", season_id.to_string()),
                ("episode", episode_id.to_string()),
                ("favs", detail.favs.clone()),
                ("action", "get_stream".to_string()),
            ],
            Some(&referer),
        )?;
        let value: serde_json::Value = resp.json().map_err(|e| CoreError::Parse {
            msg: format!("ответ сервера не JSON: {e}"),
        })?;
        if !value
            .get("success")
            .and_then(|v| v.as_bool())
            .unwrap_or(false)
        {
            let msg = value
                .get("message")
                .and_then(|v| v.as_str())
                .unwrap_or("сервер не отдал ссылки")
                .to_string();
            return Err(CoreError::Parse { msg });
        }
        if value
            .get("premium_content")
            .and_then(|v| v.as_i64())
            .unwrap_or(0)
            == 1
        {
            return Err(CoreError::Auth {
                msg: "эта озвучка доступна только с Premium".to_string(),
            });
        }
        Ok(value
            .get("url")
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_string())
    }
}

fn guard_login(html: &str) -> Result<(), CoreError> {
    if html.contains("id=\"loginForm\"") || html.contains("Вход на HDrezka") {
        Err(CoreError::Auth {
            msg: "сессия истекла, войдите заново".to_string(),
        })
    } else {
        Ok(())
    }
}
