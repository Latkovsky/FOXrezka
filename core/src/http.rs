use reqwest::blocking::{Client as HttpClient, Response};
use reqwest::header::{
    HeaderMap, HeaderValue, ACCEPT, ACCEPT_LANGUAGE, COOKIE, REFERER, USER_AGENT,
};
use std::collections::BTreeMap;
use std::sync::Mutex;
use std::time::Duration;

use crate::CoreError;

pub const BASE: &str = "https://rezka.fi";
pub const UA: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 \
    (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

pub struct Http {
    client: HttpClient,
    cookies: Mutex<BTreeMap<String, String>>,
}

impl Http {
    pub fn new() -> Self {
        let mut headers = HeaderMap::new();
        headers.insert(USER_AGENT, HeaderValue::from_static(UA));
        headers.insert(
            ACCEPT,
            HeaderValue::from_static(
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,\
                 image/webp,*/*;q=0.8",
            ),
        );
        headers.insert(
            ACCEPT_LANGUAGE,
            HeaderValue::from_static("ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"),
        );

        let client = HttpClient::builder()
            .default_headers(headers)
            .redirect(reqwest::redirect::Policy::limited(10))
            .timeout(Duration::from_secs(25))
            .build()
            .expect("failed to build http client");

        Self {
            client,
            cookies: Mutex::new(BTreeMap::new()),
        }
    }

    fn cookie_header(&self) -> Option<String> {
        let map = self.cookies.lock().unwrap();
        if map.is_empty() {
            None
        } else {
            Some(
                map.iter()
                    .map(|(k, v)| format!("{k}={v}"))
                    .collect::<Vec<_>>()
                    .join("; "),
            )
        }
    }

    fn store_cookies(&self, resp: &Response) {
        let mut map = self.cookies.lock().unwrap();
        for value in resp.headers().get_all("set-cookie") {
            if let Ok(raw) = value.to_str() {
                let pair = raw.split(';').next().unwrap_or("").trim();
                if let Some((name, val)) = pair.split_once('=') {
                    let name = name.trim();
                    if !name.is_empty() {
                        map.insert(name.to_string(), val.trim().to_string());
                    }
                }
            }
        }
    }

    fn finish(&self, resp: Response) -> Result<Response, CoreError> {
        self.store_cookies(&resp);
        let status = resp.status();
        if status.is_success() {
            Ok(resp)
        } else {
            Err(CoreError::Network {
                msg: format!("HTTP {}", status.as_u16()),
            })
        }
    }

    pub fn get(&self, url: &str) -> Result<Response, CoreError> {
        let mut req = self.client.get(url);
        if let Some(cookies) = self.cookie_header() {
            req = req.header(COOKIE, cookies);
        }
        if let Some(referer) = url_referer(url) {
            req = req.header(REFERER, referer);
        }
        let resp = req.send().map_err(net)?;
        self.finish(resp)
    }

    pub fn get_html(&self, url: &str) -> Result<String, CoreError> {
        self.get(url)?
            .text()
            .map_err(|e| CoreError::Network { msg: e.to_string() })
    }

    pub fn post_form(
        &self,
        url: &str,
        form: &[(&str, String)],
        referer: Option<&str>,
    ) -> Result<Response, CoreError> {
        let mut req = self
            .client
            .post(url)
            .header("X-Requested-With", "XMLHttpRequest");
        if let Some(cookies) = self.cookie_header() {
            req = req.header(COOKIE, cookies);
        }
        if let Some(referer) = referer {
            req = req.header(REFERER, referer);
        }
        let resp = req.form(form).send().map_err(net)?;
        self.finish(resp)
    }

    pub fn clear_cookies(&self) {
        self.cookies.lock().unwrap().clear();
    }

    pub fn export_cookies(&self) -> String {
        self.cookie_header().unwrap_or_default()
    }

    pub fn import_cookies(&self, raw: &str) {
        let mut map = self.cookies.lock().unwrap();
        for part in raw.split(';') {
            let part = part.trim();
            if let Some((name, val)) = part.split_once('=') {
                let name = name.trim();
                if !name.is_empty() && !val.trim().is_empty() {
                    map.insert(name.to_string(), val.trim().to_string());
                }
            }
        }
    }
}

fn net(err: reqwest::Error) -> CoreError {
    CoreError::Network {
        msg: err.to_string(),
    }
}

fn url_referer(url: &str) -> Option<HeaderValue> {
    if url.contains("/ajax/") {
        Some(HeaderValue::from_static(BASE))
    } else {
        None
    }
}
