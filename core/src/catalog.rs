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
    parse_cards_filtered(html, None)
}

pub fn parse_homepage_cards(html: &str) -> Vec<Card> {
    parse_cards_filtered(html, None)
}

fn parse_cards_filtered(html: &str, kind_filter: Option<&str>) -> Vec<Card> {
    let doc = Html::parse_document(html);
    let item_sel = Selector::parse("div.b-content__inline_item").unwrap();
    let img_sel = Selector::parse("img").unwrap();
    let link_sel = Selector::parse("div.b-content__inline_item-link a").unwrap();
    let misc_sel = Selector::parse("div.misc").unwrap();
    let cat_sel = Selector::parse("span.cat").unwrap();

    let mut cards = Vec::new();
    let mut seen = std::collections::HashSet::new();
    for item in doc.select(&item_sel) {
        let path = item.value().attr("data-url").unwrap_or_default();
        if path.is_empty() {
            continue;
        }

        let mut kind = "films".to_string();
        let path_lower = path.to_ascii_lowercase();
        if let Some(cat) = item.select(&cat_sel).next() {
            let classes: Vec<&str> = cat.value().classes().collect();
            if classes.contains(&"series") {
                kind = "series".to_string();
            } else if classes.contains(&"cartoons") {
                kind = "cartoons".to_string();
            } else if classes.contains(&"anime") || classes.contains(&"animation") {
                kind = "anime".to_string();
            } else if classes.contains(&"dorama") || classes.contains(&"dramy") {
                kind = "doramas".to_string();
            }
        }
        if path_lower.contains("/series/") {
            kind = "series".to_string();
        } else if path_lower.contains("/cartoons/") {
            kind = "cartoons".to_string();
        } else if path_lower.contains("/animation/") {
            kind = "anime".to_string();
        } else if path_lower.contains("/dramy/") {
            kind = "doramas".to_string();
        }

        if kind_filter.is_some_and(|filter| kind != filter) {
            continue;
        }

        let path = normalize_path(path);
        if !seen.insert(path.clone()) {
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

        cards.push(Card {
            id,
            title,
            path,
            poster,
            info,
            kind,
        });
    }
    cards
}

#[cfg(test)]
mod tests {
    use super::parse_homepage_cards;

    #[test]
    fn homepage_cards_include_films_and_series_in_source_order() {
        let html = r#"
            <div class="b-content__inline_item" data-id="1" data-url="/films/one/">
                <span class="cat films"></span>
                <div class="b-content__inline_item-link"><a>Фильм</a></div>
            </div>
            <div class="b-content__inline_item" data-id="2" data-url="/series/one/">
                <span class="cat series"></span>
                <div class="b-content__inline_item-link"><a>Сериал 1</a></div>
            </div>
            <div class="b-content__inline_item" data-id="3" data-url="/animation/anime/">
                <span class="cat animation"></span>
                <div class="b-content__inline_item-link"><a>Аниме</a></div>
            </div>
            <div class="b-content__inline_item" data-id="4" data-url="/dramy/dorama/">
                <span class="cat dramy"></span>
                <div class="b-content__inline_item-link"><a>Дорама</a></div>
            </div>
        "#;

        let cards = parse_homepage_cards(html);

        assert_eq!(cards.len(), 4);
        assert_eq!(cards[0].title, "Фильм");
        assert_eq!(cards[0].kind, "films");
        assert_eq!(cards[1].title, "Сериал 1");
        assert_eq!(cards[1].kind, "series");
        assert_eq!(cards[2].kind, "anime");
        assert_eq!(cards[3].kind, "doramas");
    }
}
