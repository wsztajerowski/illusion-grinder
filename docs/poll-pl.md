# Ankieta po prezentacji: Maszynka do Mielenia Złudzeń

**Nagłówek formularza:**

> Dziękuję za udział w prezentacji! Proszę o wypełnienie krótkiej ankiety, która pomoże mi
> ulepszyć ten materiał.

**Kształt:** 17 pozycji (14 numerowanych, z czego 13 rozbite na 13a/13b, plus pytanie
o polecenie i dwa otwarte), ok. 2–3 minuty na wypełnienie. Pytania 1–3 dają soczewkę, przez
którą czyta się wszystkie kolejne odpowiedzi — "za zaawansowane" od kogoś, kto nigdy nie
napisał testu wielowątkowego, znaczy coś zupełnie innego niż ta sama odpowiedź od inżyniera
z piętnastoletnim stażem na JVM.

`*` oznacza pytanie wymagane.

---

## A. O Tobie

### 1. Jaka część kodu, który rozwijasz, jest naprawdę współbieżna — ze współdzielonym, mutowalnym stanem między wątkami?

*Jednokrotny wybór*

- To trzon mojej pracy (kolejki, cache'e, schedulery, silniki)
- Część — kilka gorących miejsc
- Rzadko — framework załatwia to za mnie
- Żadna, o ile wiem

### 2. Których z tych narzędzi używałeś/aś przed dzisiejszą prezentacją?

*Wielokrotny wybór*

- Testów JUnit uruchamiających wątki
- Fray
- jcstress
- JMH
- Żadnego z powyższych
- Inne: \_\_\_

### 3. Ile lat zawodowo programujesz?

*Jednokrotny wybór*

- 0–2
- 3–5
- 6–10
- 10+

---

## B. Prezentacja

### 4. Ogólnie — jak oceniasz prezentację? `*`

*Skala 1–5, od "Słaba" do "Świetna"*

### 5. Poziom i głębokość `*`

*Jednokrotny wybór*

- Za podstawowe — większość już znałem/znałam
- Lekko poniżej mojego poziomu, ale i tak dobrze się słuchało
- W sam raz
- Trochę powyżej mojego poziomu, ale nadążałem/nadążałam
- Za zaawansowane — zgubiłem/zgubiłam wątek

### 6. Tempo `*`

*Jednokrotny wybór*

- Za wolne
- Trochę za wolne
- W sam raz
- Trochę za szybkie
- Za szybkie

### 7. Która część była dla Ciebie najbardziej wartościowa? `*`

*Jednokrotny wybór*

- Fray i deterministyczne odtworzenie błędu pod debuggerem
- jcstress i błąd widoczności pamięci, którego Fray nie zobaczy
- JMH — ile naprawdę kosztuje poprawność
- Zamknięcie: mapa decyzyjna i trzy złote zasady
- Trudno wybrać — wszystkie były równie wartościowe
- Inne: \_\_\_

### 8. …a którą można było skrócić albo wyciąć? `*`

*Jednokrotny wybór*

- Wprowadzenie — cztery implementacje, dwie zepsute, wszystkie na zielono
- Fray i deterministyczne odtworzenie błędu pod debuggerem
- jcstress i błąd widoczności pamięci, którego Fray nie zobaczy
- JMH — ile naprawdę kosztuje poprawność
- Zamknięcie: mapa decyzyjna i trzy złote zasady
- Żadnej — proporcje były dobre
- Inne: \_\_\_

> Wprowadzenie jest opcją tylko tutaj, nie w pytaniu 7. Rozbieg ma być niewidoczny, kiedy
> działa — pytamy więc, czy przeszkadzał, a nie czy zachwycił.

### 9. Prezentacja obiecywała, że wyjdziesz z niej wiedząc, które narzędzie zadaje które pytanie. Dowiozła? `*`

*Jednokrotny wybór*

- Tak — jutro potrafię sięgnąć po właściwe narzędzie
- W większości — przydałyby mi się slajdy pod ręką
- Częściowo — rozumiem różnicę między Fray a jcstress, ale niewiele poza tym
- Nie — dalej nie wiem, kiedy po co sięgać

> Warte więcej niż ogólna ocena: sprawdza obietnicę z abstraktu, a nie to, jak dobrze
> publiczność się bawiła.

---

## C. Prowadzący

### 10. Jak jasno wytłumaczone były koncepcje? `*`

*Skala 1–5, od "Niejasno" do "Bardzo klarownie"*

### 11. Sposób prowadzenia `*`

*Siatka, skala 1–5 dla każdego wiersza*

- Energia i dynamika głosu
- Pewność i opanowanie tematu
- Utrzymanie uwagi sali

### 12. Konwencja — "kręgi piekła", "mielenie złudzeń", "odrobina współbieżnego sadyzmu" `*`

*Jednokrotny wybór*

- Świetna — to dzięki niej struktura została mi w głowie
- Zabawna i nie przeszkadzała
- Neutralnie — przyszedłem/przyszłam po treść
- Trochę za dużo — ująłbym/ujęłabym
- Odciągała uwagę od treści technicznej

### 13a. Kod na slajdach — czytelność wizualna

*Jednokrotny wybór*

- Czytelny z miejsca, w którym siedziałem/siedziałam
- Za mały / trudny do odczytania

### 13b. Kod na slajdach — ilość

*Jednokrotny wybór*

- Odpowiednia ilość kodu
- Za dużo kodu na slajd
- Za mało — chciałem/chciałam zobaczyć więcej prawdziwego kodu

> Dwa osobne pytania zamiast jednej listy wielokrotnego wyboru: rozmiar czcionki i gęstość
> kodu to dwie różne usterki i dwie różne poprawki. Zlane w jedno pytanie dają wynik,
> z którym nic nie da się zrobić.

---

## D. Podsumowanie

### Poleciłbyś/poleciłabyś tę prezentację koledze lub koleżance z zespołu? `*`

*Jednokrotny wybór*

- Tak
- Może
- Nie

To najczystsza pojedyncza liczba do zacytowania w zgłoszeniu CFP — dlatego stoi jako
wymagane pytanie przed tymi otwartymi, a nie jako dopisek na końcu.

### 14. Jedna rzecz do zostawienia, jedna do zmiany

*Tekst otwarty, opcjonalne*

### Co chciałbyś/chciałabyś zobaczyć ode mnie następnym razem?

*Tekst otwarty, opcjonalne*

---

## Jak czytać wyniki

- **P5 × P6** — czy "za szybko" to naprawdę problem z tempem mówienia, czy z poziomem.
  Jeśli te same osoby zaznaczyły "za zaawansowane", lekarstwem jest dłuższe wprowadzenie,
  a nie wolniejsze mówienie.
- **P8 × P3** — jeśli wprowadzenie chcą wyciąć głównie seniorzy, a mniej doświadczeni
  odpowiadają "proporcje były dobre", to rozbieg robi swoje. To argument za jego
  utrzymaniem, kiedy recenzenci każą go skrócić.
- **P7 × P1** — która część trafia do osób, które współbieżność mają w codziennej pracy.
  Jeśli wybierają jcstress, a reszta sali Fray, to dwie połowy prezentacji mają dwie różne
  publiczności i obie są potrzebne.
- **P9 × ogólna ocena (P4)** — wysokie oceny przy słabych odpowiedziach na P9 znaczą, że
  prezentacja bawi, ale nie uczy. To jedyna para, która potrafi to wykryć.
