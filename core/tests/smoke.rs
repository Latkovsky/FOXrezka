use rezka_core::Client;
use std::fs;

fn cookie_string(path: &str) -> String {
    let raw = fs::read_to_string(path).unwrap_or_default();
    let mut pairs = Vec::new();
    for line in raw.lines() {
        let line = line.strip_prefix("#HttpOnly_").unwrap_or(line);
        if line.starts_with('#') || line.trim().is_empty() {
            continue;
        }
        let fields: Vec<&str> = line.split('\t').collect();
        if fields.len() >= 7 {
            pairs.push(format!("{}={}", fields[5], fields[6]));
        }
    }
    pairs.join("; ")
}

#[test]
fn smoke() {
    let path = std::env::var("REZKA_COOKIES")
        .unwrap_or_else(|_| "/tmp/opencode/rezka_cookies.txt".to_string());
    let client = Client::new();
    client.import_cookies(cookie_string(&path));

    let profile = client.profile().expect("profile");
    println!("profile: logged_in={} email={} id={}", profile.logged_in, profile.email, profile.user_id);
    assert!(profile.logged_in, "not logged in");

    let films = client.category("films".to_string()).expect("category films");
    println!("films on first page: {}", films.len());
    assert!(!films.is_empty());

    let series = client.category("series".to_string()).expect("category series");
    println!("series on first page: {}", series.len());

    let found = client.search("матрица".to_string()).expect("search");
    println!("search results: {}", found.len());
    assert!(!found.is_empty());
    println!("first: {}", found[0].title);

    let movie = client.open(found[0].path.clone()).expect("open movie");
    println!(
        "movie: '{}' / '{}' series={} rating='{}' info='{}'",
        movie.title, movie.original_title, movie.is_series, movie.rating, movie.info
    );
    println!("description: {}...", movie.description.chars().take(70).collect::<String>());
    let streams = client
        .streams(
            movie.clone(),
            movie.default_translator.clone(),
            movie.default_season.clone(),
            movie.default_episode.clone(),
        )
        .expect("movie streams");
    println!("movie qualities: {}", streams.qualities.len());
    for q in &streams.qualities {
        println!("  {} {} {}", q.label, if q.is_hls { "hls" } else { "mp4" }, &q.url[..q.url.len().min(70)]);
    }
    assert!(!streams.qualities.is_empty());

    let series_path = "/series/drama/92807-noktyurn-2026-latest.html".to_string();
    let show = client.open(series_path).expect("open series");
    println!(
        "series: '{}' series={} translators={} seasons={} default t/s/e={}/{}/{}",
        show.title,
        show.is_series,
        show.translators.len(),
        show.seasons.len(),
        show.default_translator,
        show.default_season,
        show.default_episode
    );
    for s in &show.seasons {
        println!("  season {} '{}' episodes={}", s.id, s.title, s.episodes.len());
    }
    let st = client
        .streams(
            show.clone(),
            show.default_translator.clone(),
            show.default_season.clone(),
            show.default_episode.clone(),
        )
        .expect("series streams");
    println!("series qualities: {}", st.qualities.len());
    for q in &st.qualities {
        println!("  {} {} {}", q.label, if q.is_hls { "hls" } else { "mp4" }, &q.url[..q.url.len().min(70)]);
    }
    assert!(!st.qualities.is_empty());
}
