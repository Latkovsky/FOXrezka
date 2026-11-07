use scraper::{Html, Selector};

use crate::http::BASE;

#[derive(Clone, Debug, uniffi::Record)]
pub struct Card {
    pub id: String,
    pub title: String,
    pub path: String,
    pub poster: String,
    pub info: String,
    pub kind: String,
}

pub fn normalize_path(path: &str) -> String {
    let path = path.trim();
    if path.starts_with("http://") || path.starts_with("https://") {
        path.to_string()
    } else if path.starts_with('/') {
        format!("{BASE}{path}")
    } else {
        format!("{BASE}/{path}")
    }
}

pub fn parse_cards(html: &str) -> Vec<Card> {
    let doc = Html::parse_document(html);
    let item_sel = Selector::parse("div.b-content__inline_item").unwrap();
    let img_sel = Selector::parse("img").unwrap();
    let link_sel = Selector::parse("div.b-content__inline_item-link a").unwrap();
    let misc_sel = Selector::parse("div.misc").unwrap();
    let cat_sel = Selector::parse("span.cat").unwrap();

    let mut cards = Vec::new();
    for item in doc.select(&item_sel) {
        let path = item.value().attr("data-url").unwrap_or_default();
        if path.is_empty() {
            continue;
        }
        let id = item.value().attr("data-id").unwrap_or_default().to_string();
        let poster = item
            .select(&img_sel)
            .next()
            .and_then(|i| i.value().attr("src"))
            .unwrap_or_default()
            .to_string();
        let title = item
            .select(&link_sel)
            .next()
            .map(|a| a.text().collect::<String>().trim().to_string())
            .unwrap_or_default();
        let info = item
            .select(&misc_sel)
            .next()
            .map(|d| d.text().collect::<String>().trim().to_string())
            .unwrap_or_default();

        let mut kind = "films".to_string();
        if let Some(cat) = item.select(&cat_sel).next() {
            let classes: Vec<&str> = cat.value().classes().collect();
            if classes.contains(&"series") {
                kind = "series".to_string();
            } else if classes.contains(&"cartoons") {
                kind = "cartoons".to_string();
            }
        }

        cards.push(Card {
            id,
            title,
            path: normalize_path(path),
            poster,
            info,
            kind,
        });
    }

    // keep order but drop duplicates (same film can appear in several blocks)
    let mut seen = std::collections::HashSet::new();
    cards.retain(|c| seen.insert(c.path.clone()));
    cards
}
