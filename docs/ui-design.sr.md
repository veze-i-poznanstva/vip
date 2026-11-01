# Istraživač grafa: dizajn korisničkog interfejsa

*English version: [ui-design.md](ui-design.md)*

Status: inkrementi 2, 3 i polovina 5 (proširivanje) su izgrađeni (videti Inkrementi); ostalo je dizajn. Ovaj dokument opisuje prvi pravi korisnički interfejs VIP-a,
koji menja postojeći demo pregledač što sav skup podataka istovremeno izbacuje na jedno platno.

## Kome je namenjen i šta korisnici rade

Istražitelj ili novinar dolazi sa imenom. Želi da za manje od minuta odgovori na pitanje:
s kim je ta osoba povezana, preko čega i gde je dokaz. Zatim prati jednu od tih veza i ponovo
postavlja isto pitanje. Interfejs je trag, a ne mapa.

Tri stvari uvek moraju da važe na ekranu:

1. Osoba ili organizacija u fokusu je nepogrešivo uočljiva.
2. Prikazuje se samo neposredno susedstvo, tako da slika ostaje čitljiva.
3. Izvori iza svakog čvora ili ivice su na jedan klik, sa statusom provere.

## Tok korišćenja

1. **Pretraga.** Korisnik kuca u jedno polje za pretragu. Predlozi se pojavljuju posle dva
   znaka i poklapaju imena, imena i prezimena, kao i aliase. Poklapanje ne razlikuje dijakritike
   i velika i mala slova, pa „brnabic“ pronalazi „Ana Brnabić“. Ćirilični unos se pre
   poklapanja transliteruje u latinicu, pa „Брнабић“ pronalazi istu osobu.
2. **Fokus.** Izbor predloga iscrtava ego graf: čvor u fokusu u sredini i svi čvorovi
   neposredno povezani s njim u prstenu oko njega. Prekidač dubine menja prikaz između jednog
   koraka (podrazumevano) i dva koraka. Dva koraka iscrtavaju drugi prsten bledije.
3. **Pregled.** Jedan klik na bilo koji čvor ili ivicu bira ga i popunjava panel sa detaljima:
   oznaku, tip, sva poznata svojstva i listu izvora sa domenom i statusom dostupnosti.
   Ništa drugo na platnu se ne menja.
4. **Proširivanje.** Dvoklik, ili dugme Proširi u panelu sa detaljima, dodaje susede tog čvora
   na platno bez uklanjanja onoga što je već tu. Raspored se animira tako da se prošireni čvor
   pomera ka spolja, a njegovi susedi se okupljaju oko njega.
5. **Ponovno centriranje.** Dugme Fokus u panelu sa detaljima čini izabrani čvor novim centrom
   i ponovo gradi ego graf oko njega. Prethodni fokus se dodaje u trag.
6. **Praćenje traga.** Traka sa putanjom pri dnu navodi svaki čvor u fokusu posećen u ovoj
   sesiji. Klik na stavku ponovo centrira na nju. Trag je kodiran u heš delu URL-a, pa veza
   kolegi reprodukuje isti prikaz.

## Raspored ekrana

```
+----------------------------------------------------------------------------------+
| [ Pretraži osobu ili organizaciju...                     ]  Dubina: (1) ( ) 2    |
+-------------------------------------------------------------+--------------------+
|                                                             | Ana Brnabić        |
|                   ( Milenijum Tim )                         | Osoba              |
|                        |  vlasnik                           |--------------------|
|   ( Peta Gimnazija )---( ANA BRNABIĆ )---( Ivan Bošnjak )   | Rođ. 1975-09-28    |
|        školski drug    |  poslovni partner                  | Stranka: SNS       |
|                   ( SNS )                                   |--------------------|
|                      član                                   | Izvori (2)         |
|                                                             | ✓ istinomer.rs     |
|                                                             | ✗ sr.wikipedia.org |
|                                                             |   HTTP 403         |
|                                                             |--------------------|
|                                                             | [Fokus] [Proširi]  |
+-------------------------------------------------------------+--------------------+
| Trag:  Siniša Mali  >  Peta Gimnazija  >  Ana Brnabić                            |
+----------------------------------------------------------------------------------+
```

Na uskim ekranima panel sa detaljima postaje list koji klizi preko platna.

## Vizuelno kodiranje

- Oblik i boja čvora zavise od tipa, kao i sada: osoba je elipsa, organizacija pravougaonik,
  stranka romb, akademska ustanova zaobljeni pravougaonik. Nepoznati tipovi su sivi.
- Čvor u fokusu je veći i ima debeo obrub. Čvorovi prvog prstena su potpuno neprozirni, čvorovi
  drugog prstena smanjene neprozirnosti. Čvorovi dodati proširivanjem su potpuno neprozirni.
- Oznake ivica su podrazumevano skrivene, a prikazuju se pri prelasku mišem i pri izboru, da bi
  platno ostalo čisto.
- Raspored: koncentričan oko čvora u fokusu za ego prikaz; pri proširivanju pokreće se
  inkrementalni raspored zasnovan na silama sa zaključanim postojećim čvorovima, da slika ne
  skače.
- Ivice čija veza nema nijedan dostupan izvor crtaju se isprekidano, tako da se slab dokaz vidi
  na prvi pogled.

## Podaci

Skup podataka će u doglednoj budućnosti ostati mali, pa je prva verzija u potpunosti na strani
klijenta. Build izvozi jedinstven `graph.json` koji pregledač učitava jednom i indeksira u
memoriji.

```json
{
  "nodes": [
    { "id": "ana_brnabic", "uri": "...", "label": "Ana Brnabić", "type": "person",
      "aliases": ["Ана Брнабић"], "properties": { "birthDate": "1975-09-28" },
      "sources": [ { "url": "https://...", "domain": "istinomer.rs", "status": "ok" } ] }
  ],
  "edges": [
    { "id": "c1", "source": "ana_brnabic", "target": "ivan_bosnjak",
      "label": "business partner", "connectionUri": "...", "comment": "...",
      "sources": [ ... ] }
  ]
}
```

Susedstvo se izračunava u pregledaču iz liste ivica. Pretraga radi nad svedenim indeksom (mala
slova, uklonjeni dijakritici, ćirilica transliterisana u latinicu) koji se gradi pri učitavanju.

Kada skup podataka preraste nekoliko hiljada čvorova, isti interfejs prelazi na pozadinski
servis: Fuseki SPARQL krajnju tačku iza `/api`, na koju Vite razvojni proksi već pokazuje. Dva
upita pokrivaju interfejs: pretraga po svedenoj oznaci i susedstvo URI-ja na dubini jedan ili
dva. JSON ugovor iznad ostaje isti, pa se interfejs ne menja.

## Potrebne izmene u konverteru

Trenutni konverter proizvodi čvorove i ivice samo sa poljima id, label i type. Takođe mora da:

- prenese svaki `hasDocumentURL` i `hasSource` čvora na čvor, a veze na ivicu u koju je
  sažeta;
- zadrži URI i komentar veze na ivici, da ih panel sa detaljima može prikazati;
- prikupi aliase iz svih vrednosti `foaf:name` i ćiriličnih i latiničnih zapisa;
- zadrži vezu kao mali čvor kada ima više od dva učesnika, jer se hiperivica ne može nacrtati
  kao jedna linija;
- spoji izveštaj o verodostojnosti u izvoz, tako da svaki izvor nosi svoj poslednji poznati
  status.

## Panel sa izvorima

Ovaj panel je razlog postojanja projekta i zaslužuje najviše pažnje. Za svaki izvor: domen
podebljano, pun URL pri prelasku mišem, status dostupnosti sa HTTP kodom, datum poslednje
provere i arhivirana kopija. Arhivirana kopija je primarna veza, jer njena adresa u sebi nosi
datum snimanja i ne može se naknadno menjati; živi URL je sekundaran. Izvori sa istog domena se
grupišu da bi pravilo o dva domena bilo vidljivo. Entitet ili ivica koji krši pravilo prikazuje
baner sa upozorenjem na vrhu panela.

## Inkrementi

Svaki inkrement je tanak vertikalni presek koji se završava zelenim, po pristupu malih koraka.

1. **Izvoz iz konvertera.** Čvorovi i ivice nose izvore, aliase, URI veze i komentar.
   Kotlin jedinični testovi.
2. **Pretraga.** Polje za pretragu sa automatskim dopunjavanjem nad svedenim indeksom.
   Jedinični testovi za svođenje i poklapanje u Vitest-u, uključujući ćirilični unos i
   dijakritike.
3. **Ego graf, dubina jedan.** Izbor rezultata iscrtava čvor u fokusu i njegove susede
   koncentričnim rasporedom.
4. **Panel sa detaljima.** Klik prikazuje svojstva i izvore sa statusom.
5. **Proširivanje i ponovno centriranje.** Dvoklik proširuje; dugme Fokus ponovo centrira;
   pojavljuje se trag.
6. **Prekidač dubine dva.** Drugi prsten smanjene neprozirnosti.
7. **Duboke veze.** Trag i skup proširenih čvorova u heš delu URL-a.
8. **Izvoz statusa verodostojnosti.** Build upisuje izveštaj o verodostojnosti kao JSON, a
   konverter ga spaja, pa isprekidane ivice i oznake statusa odražavaju poslednju proveru.

Inkrementi jedan i dva sami donose vrednost i mogu se pregledati nezavisno.

## Otvorena pitanja

- **Ćirilica i latinica.** Za sada odlučeno: svođenje u interfejsu transliteruje srpsku
  ćirilicu u latinicu, pa oba pisma nalaze istu oznaku. Imena stranog porekla čiji ćirilični
  zapis nije transliteracija slovo po slovo i dalje traže alias u podacima.
- **Veze sa mnogo učesnika.** Upravni odbor je jedna veza sa mnogo učesnika. Prikaz kao čvor
  je ispravan, ali se vizuelno razlikuje od parnih ivica. Potreban je vizuelni tretman.
- **Status demoa.** [Alati](tools.sr.md) označavaju veb modul kao samo demo. Kada se isporuči treći inkrement,
  modul postaje interfejs proizvoda i ta stranica to treba da kaže.
- **Testiranje platna.** Iscrtavanje u Cytoscape-u je teško jedinično testirati. Izgradnju
  grafa (čvorovi, ivice, susedstvo, svođenje, stanje traga) treba držati u običnim TypeScript
  modulima sa Vitest testovima, a Svelte komponente ostaviti kao tanke ljuske za iscrtavanje.
