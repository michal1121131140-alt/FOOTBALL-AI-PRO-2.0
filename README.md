# FOOTBALL AI PRO 2.0

Aplikacja Android do analizy spotkań piłkarskich, statystyk drużyn i oceny prawdopodobieństwa rynków bukmacherskich.

## Funkcje

- **Mecze i rozgrywki:** terminarze i wyniki z OpenLigaDB dla dostępnych tam lig.
- **Globalne mecze:** wyszukiwanie spotkań według daty z API-Football, z filtrowaniem po lidze i drużynie.
- **Tabele:** tabela ligowa dla rozgrywek obsługiwanych przez OpenLigaDB.
- **Analiza drużyn:** bilans, średnie goli, podział dom/wyjazd, ostatnia forma i H2H.
- **Prognozy:** model Poissona dla 1X2, sumy goli i BTTS, a także pięć najbardziej prawdopodobnych dokładnych wyników.
- **TOP 10:** ranking spotkań na podstawie prognozy i wiarygodności danych.
- **Value Betting:** porównanie prawdopodobieństwa modelu z kursem dziesiętnym.
- **Historia typów:** zapisywanie typów, automatyczne rozliczanie wyników, skuteczność, ROI i bilans.
- **Test historyczny:** sprawdzanie prognoz na podstawie wcześniejszych wyników, bez wykorzystywania wyniku analizowanego meczu.

## Źródła danych i konfiguracja

### OpenLigaDB

Zakładki MECZE i TABELA korzystają z publicznego API OpenLigaDB. Dostępność lig i sezonów zależy od zakresu danych udostępnianych przez ten serwis.

### API-Football

Zakładka GLOBAL korzysta z API-Football (API-Sports). Wymaga własnego klucza API:

1. Załóż konto w API-Sports i wygeneruj klucz API-Football.
2. Otwórz zakładkę GLOBAL w aplikacji.
3. Wklej klucz i wybierz ZAPISZ KLUCZ.
4. Wybierz SPRAWDŹ API, a następnie pobierz mecze dla wybranej daty.

Klucz jest przechowywany lokalnie w prywatnych preferencjach aplikacji i nie powinien być dodawany do repozytorium. Liczba dostępnych lig, kursów, prognoz i absencji zależy od planu API-Football oraz limitów zapytań. Aplikacja buforuje odpowiedzi, aby ograniczać liczbę wywołań.

## Jak działa model

Prognozy 1X2 i rynków goli są obliczane statystycznie metodą Poissona. Model wykorzystuje dostępne statystyki drużyn, rozdział meczów domowych i wyjazdowych, ostatnią formę oraz H2H. Nie jest to samouczący się model AI ani gwarancja wyniku.

Wiarygodność danych opisuje ilość dostępnych danych, a nie prawdopodobieństwo wygranej. Dodatnie EV oznacza matematyczną przewagę przy założeniach modelu, nie pewny zysk.

Test historyczny obejmuje tylko mecze z aktualnie załadowanej ligi i sezonu. Spotkania z tego samego dnia są wykluczane z próby historycznej, ponieważ źródłowe daty nie zawsze pozwalają ustalić kolejność spotkań w obrębie dnia.

## Budowanie aplikacji

Projekt używa Kotlin, Android Gradle Plugin i Jetpack Compose.

GitHub Actions uruchamia testy jednostkowe, a następnie buduje APK debug. Gotowy plik APK znajduje się w artefaktach workflow **Build Android APK**.

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
