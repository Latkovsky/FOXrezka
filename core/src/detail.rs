use regex::Regex;
use scraper::{Html, Selector};

use crate::catalog::normalize_path;
use crate::CoreError;

#[derive(Clone, Debug, uniffi::Record)]
pub struct Translator {
    pub id: String,
    pub name: String,
    pub premium: bool,
    pub active: bool,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct Episode {
    pub season_id: String,
    pub episode_id: String,
    pub title: String,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct Season {
    pub id: String,
    pub title: String,
    pub episodes: Vec<Episode>,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct MovieDetail {
    pub id: String,
    pub path: String,
    pub title: String,
    pub original_title: String,
    pub description: String,
    pub poster: String,
    pub info: String,
    pub rating: String,
    pub is_series: bool,
    pub translators: Vec<Translator>,
    pub seasons: Vec<Season>,
    pub default_translator: String,
    pub default_season: String,
    pub default_episode: String,
    pub favs: String,
    pub streams_raw: String,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct Quality {
    pub label: String,
    pub url: String,
    pub is_hls: bool,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct StreamSet {
    pub qualities: Vec<Quality>,
}

pub fn parse_detail(html: &str, path: &str) -> Result<MovieDetail, CoreError> {
    let doc = Html::parse_document(html);

    let title = text_of(&doc, "div.b-post__title h1");
    if title.is_empty() {
        return Err(CoreError::Parse {
            msg: "не удалось разобрать страницу фильма".to_string(),
        });
    }
    let original_title = text_of(&doc, "div.b-post__origtitle");
    let description = text_of(&doc, "div.b-post__description_text");
    let poster = doc
        .select(&Selector::parse("div.b-sidecover img").unwrap())
        .next()
        .and_then(|e| e.value().attr("src"))
        .unwrap_or_default()
        .to_string();

    let rows = info_rows(&doc);
    let mut info_parts: Vec<String> = Vec::new();
    for key in ["Дата выхода", "Страна", "Жанр", "Время"] {
        if let Some(v) = rows.get(key) {
            let v = v.trim();
            if !v.is_empty() {
                info_parts.push(v.to_string());
            }
        }
    }
    let info = info_parts.join(" • ");

    let rating = rows
        .get("Рейтинги")
        .and_then(|v| {
            Regex::new(r"\d+[.,]\d+")
                .ok()
                .and_then(|re| re.find(v).map(|m| m.as_str().replace(',', ".")))
        })
        .unwrap_or_default();

    let mut translators = Vec::new();
    if let Ok(sel) = Selector::parse("li.b-translator__item") {
        for item in doc.select(&sel) {
            let id = item
                .value()
                .attr("data-translator_id")
                .unwrap_or_default()
                .to_string();
            if id.is_empty() {
                continue;
            }
            let name = item.text().collect::<String>().trim().to_string();
            let classes: Vec<&str> = item.value().classes().collect();
            translators.push(Translator {
                id,
                name,
                premium: classes.contains(&"b-prem_translator"),
                active: classes.contains(&"active"),
            });
        }
    }

    let mut seasons = Vec::new();
    let season_titles = season_titles(&doc);
    if let Ok(sel) = Selector::parse("ul[id^=\"simple-episodes-list-\"]") {
        for ul in doc.select(&sel) {
            let season_id = ul
                .value()
                .id()
                .unwrap_or_default()
                .trim_start_matches("simple-episodes-list-")
                .to_string();
            if season_id.is_empty() {
                continue;
            }
            let mut episodes = Vec::new();
            if let Ok(ep_sel) = Selector::parse("li.b-simple_episode__item") {
                for li in ul.select(&ep_sel) {
                    let ep_id = li
                        .value()
                        .attr("data-episode_id")
                        .unwrap_or_default()
                        .to_string();
                    let s_id = li
                        .value()
                        .attr("data-season_id")
                        .unwrap_or_default()
                        .to_string();
                    if ep_id.is_empty() {
                        continue;
                    }
                    episodes.push(Episode {
                        season_id: s_id,
                        episode_id: ep_id,
                        title: li.text().collect::<String>().trim().to_string(),
                    });
                }
            }
            seasons.push(Season {
                id: season_id.clone(),
                title: season_titles
                    .get(&season_id)
                    .cloned()
                    .unwrap_or_else(|| format!("Сезон {season_id}")),
                episodes,
            });
        }
    }
    seasons.sort_by_key(|s| s.id.parse::<u32>().unwrap_or(0));

    let id = Regex::new(r"initCDN(?:Movies|Series)Events\(\s*(\d+)")
        .ok()
        .and_then(|re| re.captures(html).map(|c| c[1].to_string()))
        .unwrap_or_default();

    let series_re =
        Regex::new(r"initCDNSeriesEvents\(\s*\d+,\s*(\d+),\s*(\d+),\s*(\d+)").unwrap();
    let movie_re = Regex::new(r"initCDNMoviesEvents\(\s*\d+,\s*(\d+)").unwrap();
    let (default_translator, default_season, default_episode) =
        if let Some(c) = series_re.captures(html) {
            (c[1].to_string(), c[2].to_string(), c[3].to_string())
        } else if let Some(c) = movie_re.captures(html) {
            (c[1].to_string(), String::new(), String::new())
        } else {
            (String::new(), String::new(), String::new())
        };

    let favs = doc
        .select(&Selector::parse("input#ctrl_favs").unwrap())
        .next()
        .and_then(|e| e.value().attr("value"))
        .unwrap_or_default()
        .to_string();

    let streams_raw = extract_streams_raw(html).unwrap_or_default();
    let is_series = !seasons.is_empty() || path.contains("/series/");

    Ok(MovieDetail {
        id,
        path: normalize_path(path),
        title,
        original_title,
        description,
        poster,
        info,
        rating,
        is_series,
        translators,
        seasons,
        default_translator,
        default_season,
        default_episode,
        favs,
        streams_raw,
    })
}

pub fn parse_streams(raw: &str) -> Vec<Quality> {
    let raw = raw.replace("\\/", "/");
    let mut result: Vec<Quality> = Vec::new();

    for chunk in raw.split(",[") {
        let chunk = chunk.trim().trim_start_matches('[');
        let Some((label, rest)) = chunk.split_once(']') else {
            continue;
        };
        // premium-only quality entries are wrapped in a special span; skip them
        if label.contains("pjs-prem-quality") {
            continue;
        }
        let label = Regex::new(r"<[^>]*>")
            .map(|re| re.replace_all(label, "").to_string())
            .unwrap_or_else(|_| label.to_string());
        let label = label.trim();
        if label.is_empty() {
            continue;
        }
        let urls: Vec<&str> = rest
            .split(" or ")
            .map(|u| u.trim())
            .filter(|u| u.starts_with("http"))
            .collect();
        if urls.is_empty() {
            continue;
        }
        let chosen = urls
            .iter()
            .find(|u| u.contains("manifest.m3u8"))
            .copied()
            .unwrap_or(urls[0]);
        result.push(Quality {
            label: label.to_string(),
            url: chosen.replace("\\/", "/"),
            is_hls: chosen.contains("manifest.m3u8"),
        });
    }

    result.sort_by_key(|q| {
        let digits: String = q.label.chars().take_while(|c| c.is_ascii_digit()).collect();
        std::cmp::Reverse(digits.parse::<u32>().unwrap_or(0))
    });
    result
}

fn extract_streams_raw(html: &str) -> Option<String> {
    let anchor = html.find("\"id\":\"cdnplayer\"")?;
    let key = "\"streams\":\"";
    let rel = html[anchor..].find(key)?;
    let start = anchor + rel + key.len() - 1; // position of opening quote of the value
    let bytes = html.as_bytes();
    let mut i = start + 1;
    let mut escaped = false;
    while i < bytes.len() {
        let b = bytes[i];
        if escaped {
            escaped = false;
        } else if b == b'\\' {
            escaped = true;
        } else if b == b'"' {
            break;
        }
        i += 1;
    }
    if i >= bytes.len() {
        return None;
    }
    serde_json::from_str::<String>(&html[start..=i]).ok()
}

fn text_of(doc: &Html, css: &str) -> String {
    Selector::parse(css)
        .ok()
        .and_then(|sel| doc.select(&sel).next())
        .map(|e| e.text().collect::<String>().trim().to_string())
        .unwrap_or_default()
}

fn info_rows(doc: &Html) -> std::collections::BTreeMap<String, String> {
    let mut map = std::collections::BTreeMap::new();
    let Ok(row_sel) = Selector::parse("div.b-post__infotable_right tr") else {
        return map;
    };
    let Ok(label_sel) = Selector::parse("td.l h2") else {
        return map;
    };
    let Ok(cell_sel) = Selector::parse("td") else {
        return map;
    };
    for row in doc.select(&row_sel) {
        let Some(label_el) = row.select(&label_sel).next() else {
            continue;
        };
        let label = label_el
            .text()
            .collect::<String>()
            .trim()
            .trim_end_matches(':')
            .to_string();
        let cells: Vec<_> = row.select(&cell_sel).collect();
        if cells.len() < 2 {
            continue;
        }
        let value = cells[1]
            .text()
            .collect::<String>()
            .split_whitespace()
            .collect::<Vec<_>>()
            .join(" ");
        map.insert(label, value);
    }
    map
}

fn season_titles(doc: &Html) -> std::collections::BTreeMap<String, String> {
    let mut map = std::collections::BTreeMap::new();
    let Ok(sel) = Selector::parse("ul#simple-seasons-tabs li") else {
        return map;
    };
    for li in doc.select(&sel) {
        if let Some(id) = li.value().attr("data-tab_id") {
            map.insert(id.to_string(), li.text().collect::<String>().trim().to_string());
        }
    }
    map
}
