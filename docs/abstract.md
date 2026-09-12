# Maszynka do Mielenia Złudzeń: Cztery Kręgi Testowego Piekła dla Kodu Współbieżnego w Javie

## Abstrakt

Twój kod współbieżny to stabilna konstrukcja czy domek z kart, któremu akurat nie wieje? Podczas prezentacji przepuścimy przykładowy kawałek współbieżnego kodu przez autorski framework — maszynkę do mielenia złudzeń. To cztery kręgi testowego piekła, gdzie każda faza to rygorystyczna bramka, którą trzeba zaliczyć, by iść dalej: od naiwnych Unit Testów, przez chłostę przeplotów we Fray i brutalny wycisk na sprzęcie w jcstress, aż po wydajnościowy rachunek sumienia w JMH. Zero magii, sama brutalna inżynieria i odrobina współbieżnego sadyzmu.

## Opis

Zielone testy jednostkowe to za mało, żeby ufać kodowi współbieżnemu — a mimo to większość zespołów na nich poprzestaje. Ta prezentacja pokazuje, dlaczego to złudzenie, i jak je systematycznie rozbić.

Na przykładzie cyklicznego bufora Lamporta — czterech implementacji tego samego interfejsu, z których dwie są celowo złamane i wszystkie cztery przechodzą CI — przechodzimy przez cztery warstwy testowego rygoru: JUnit (weryfikacja logiki algorytmu), Fray (systematyczna eksploracja wszystkich przeplotów wątków), jcstress (weryfikacja poprawności względem Java Memory Model na prawdziwym hardware'ze) i JMH (koszt poprawności w ops/s).

Każda warstwa łapie inne demony. Fray konstruuje konkretny splot prowadzący do NPE w "zoptymalizowanej" szybkiej ścieżce — i pozwala go odtworzyć deterministycznie z debuggerem. jcstress wykrywa ~35 000 wykonań z niewidocznym zapisem producenta spośród 600 milionów prób — błąd widoczności pamięci, którego Fray strukturalnie nie może zobaczyć. JMH zamyka pętlę: lock-free volatile bije lock-based 3× przepustowością, ale tylko wtedy, gdy implementacja jest wcześniej udowodniona jako poprawna.

Słuchacze wychodzą z konkretną mapą decyzyjną: które narzędzie zadaje jakie pytanie i czego bez pozostałych nie widzi. Prezentacja zawiera działające demo repozytorium z kompletem testów.