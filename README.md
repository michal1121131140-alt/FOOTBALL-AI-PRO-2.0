# FOOTBALL AI PRO 2.0.3

Aplikacja Android do analizy spotkań piłkarskich, statystyk drużyn i oceny prawdopodobieństwa rynków bukmacherskich.

## Zmiany w 2.0.3

- Czytelniejsze komunikaty błędów API-Football, w tym błędnego klucza, limitu zapytań i ograniczeń planu.
- Zabezpieczenie ekranu przed opóźnionymi komunikatami z poprzednio wybranej ligi, sezonu, daty lub meczu.
- Dokładniejszy test historyczny: dopasowanie nazw drużyn bez rozróżniania wielkości liter i testy minimalnej liczby wcześniejszych spotkań.
- Bezpieczniejsza migracja starszych kluczy API: aplikacja nie wraca do używania klucza w postaci jawnego tekstu, gdy szyfrowanie się nie powiedzie.
- Komunikaty o odpowiedzialnym korzystaniu z analiz i zakładów.
- Integracja GLOBAL z TOP 10 i testem historycznym: pełny sezon wybranej ligi API-Football może zasilać formę, H2H, statystyki drużyn i prognozy; zakończone wyniki rozliczają zapisane typy.

## Funkcje

- **Mecze i rozgrywki:** terminarze i wyniki z OpenLigaDB dla dostępnych tam lig.
- **Globalne mecze:** wyszukiwanie spotkań według daty z API-Football, filtrowanie po lidze, kraju i drużynie, wybór sezonu oraz ręczne odświeżanie wyników na żywo. Przycisk „POBIERZ SEZON I MECZE DO TOP” ładuje historię wybranej ligi i bieżące mecze dnia, udostępniając dane zakładkom TOP 10, ANALIZA i STATYSTYKI.
- **Tabele:** tabele OpenLigaDB oraz tabele API-Football dla wybranej ligi i sezonu.
- **Analiza drużyn:** bilans, średnie goli, podział dom/wyjazd, ostatnia forma i H2H.
- **Prognozy:** model Poissona dla 1X2, sumy goli i BTTS, a także pięć najbardziej prawdopodobnych dokładnych wyników.
- **TOP 10:** ranking spotkań na podstawie prognozy i wiarygodności danych.
- **Value Betting:** porównanie prawdopodobieństwa modelu z kursem dziesiętnym.
- **Historia typów:** zapisywanie typów, automatyczne rozliczanie wyników (również dla pobranych zakończonych meczów API-Football), skuteczność, ROI, bilans, wyniki według rynku i eksport do CSV.
- **Test historyczny:** sprawdzanie prognoz na podstawie wcześniejszych wyników, bez wykorzystywania wyniku analizowanego meczu.
- **Eksport danych:** eksport historii typów i zapisanych spotkań do CSV.

## Źródła danych i konfiguracja

### OpenLigaDB

Zakładki MECZE i TABELA korzystają z publicznego API OpenLigaDB. Dostępność lig i sezonów zależy od zakresu danych udostępnianych przez ten serwis.

### API-Football

Zakładka GLOBAL korzysta z API-Football (API-Sports). Wymaga własnego klucza API:

1. Załóż konto w API-Sports i wygeneruj klucz API-Football.
2. Otwórz zakładkę GLOBAL w aplikacji.
3. Wklej klucz i wybierz ZAPISZ KLUCZ.
4. Wybierz SPRAWDŹ API, a następnie pobierz mecze dla wybranej daty.

Klucz jest szyfrowany lokalnie algorytmem AES-256-GCM, a klucz szyfrujący jest przechowywany w Android Keystore. Klucz API nie jest dodawany do repozytorium. Starszy zapis tekstowy jest migrowany do formatu szyfrowanego przy pierwszym odczycie. W zakładce GLOBAL można też usunąć klucz i wyczyścić lokalną pamięć odpowiedzi. Pamięć podręczna jest ograniczana do najnowszych wpisów; wyniki live są buforowane przez 30 sekund. Liczba dostępnych lig, kursów, prognoz i absencji zależy od planu API-Football oraz limitów zapytań. Aplikacja buforuje odpowiedzi, aby ograniczać liczbę wywołań.

## Jak działa model

Prognozy 1X2 i rynków goli są obliczane statystycznie metodą Poissona. Model wykorzystuje dostępne statystyki drużyn, rozdział meczów domowych i wyjazdowych, ostatnią formę oraz H2H. Nie jest to samouczący się model AI ani gwarancja wyniku.

Wiarygodność danych opisuje ilość dostępnych danych, a nie prawdopodobieństwo wygranej. Dodatnie EV oznacza matematyczną przewagę przy założeniach modelu, nie pewny zysk.

Test historyczny obejmuje tylko mecze z aktualnie załadowanej ligi i sezonu. Spotkania z tego samego dnia są wykluczane z próby historycznej, ponieważ źródłowe daty nie zawsze pozwalają ustalić kolejność spotkań w obrębie dnia.

## Budowanie aplikacji

Projekt używa Kotlin, Android Gradle Plugin i Jetpack Compose.

GitHub Actions uruchamia testy jednostkowe, a następnie buduje APK debug. Debug APK używa stałego, jawnie nieprodukcyjnego klucza podpisu, aby kolejne kompilacje można było instalować jako aktualizacje bez konfliktu podpisu. Tego klucza nie wolno używać do publikacji w Google Play. Do wydania produkcyjnego potrzebny jest osobny, prywatny klucz release. Pierwsza wersja ze stałym podpisem to 2.0.1; wcześniejsze debug APK mogły mieć inny podpis. Jeśli Android zablokuje aktualizację, wyeksportuj historię do CSV, a dopiero potem odinstaluj starszą wersję. Gotowy plik APK znajduje się w artefaktach workflow **Build Android APK**.

Lokalne polecenia:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

## Ważne ograniczenia

- „Wszystkie ligi” oznaczają ligi faktycznie dostępne w wybranym źródle i planie API, nie gwarancję pełnego pokrycia każdej federacji i sezonu.
- Kursy, kontuzje i prognozy API-Football mogą nie być dostępne dla każdego spotkania.
- Kursy można też wprowadzać ręcznie w analizie meczu.
- Aplikacja jest narzędziem statystycznym i informacyjnym, a nie usługą gwarantującą zysk.
