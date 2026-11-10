# Istraživač grafa: dizajn korisničkog interfejsa

*English version: [ui-design.md](ui-design.md)*

Status: inkrementi 2, 3, 4 (panel sa detaljima) i polovina 5 (proširivanje) su izgrađeni, kao i deo 7 za fokus (videti Inkrementi); ostalo je dizajn. Ovaj dokument opisuje prvi pravi korisnički interfejs VIP-a,
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
4. **Panel sa detaljima.** Izgrađeno. Čvor u fokusu ili izabrana veza prikazuju podatke, veze, izvore sa citatima i vezama ka arhivi, i linkove ka sirovim podacima.
5. **Proširivanje i ponovno centriranje.** Dvoklik proširuje; dugme Fokus ponovo centrira;
   pojavljuje se trag.
6. **Prekidač dubine dva.** Drugi prsten smanjene neprozirnosti.
7. **Duboke veze.** Trag i skup proširenih čvorova u heš delu URL-a. Do sada izgrađeno: putanja (`?from=&to=`) i fokus (`?focus=`).
8. **Izvoz statusa verodostojnosti.** Build upisuje izveštaj o verodostojnosti kao JSON, a
   konverter ga spaja, pa isprekidane ivice i oznake statusa odražavaju poslednju proveru.

Inkrementi jedan i dva sami donose vrednost i mogu se pregledati nezavisno.

## Upiti u pregledaču

Pravila iz `rules/`, i svako pitanje koje novinar može da iskaže kao obrazac u grafu, izvršavaju se
u posetiočevom pregledaču nad istim podacima koje mapa crta. Ništa se ne računa na serveru i ništa
ne napušta pregledač osim onoga što posetilac sam odluči da preda svom AI asistentu.

### Odluka

- **Mašina za upite.** Comunica, izdanje RDF/JS lite, čist TypeScript, nad N3.js skladištem u
  memoriji. Oko 225 KB gzipovano, učitava se tek kada se otvori panel kome treba. Bez
  WebAssembly-ja: mašina je čitljiva, može da se debaguje i radi u Vitestu bez binarne datoteke.
  Lite izdanje nema ključnu reč `SERVICE`, pa upit ne može da pozove tuđi endpoint.
- **Jedna definicija svakog pravila.** Pravila ostaju SPARQL datoteke u `rules/`. `checkRedFlags`
  u bildu i dalje izveštava u komandnoj liniji; pregledač izvršava isti tekst.
- **Tragovi se računaju u klijentu.** Ovo zamenjuje raniju ideju o unapred izračunatoj datoteci
  tragova: kada je mašina u pregledaču, nema više šta da se duplira.

### Objavljene datoteke

Bild upisuje, pored JSON-a grafa i iz istog komita:

- `data.ttl`: sve datoteke podataka spojene, uz RDFS zaključke od kojih pravila zavise (tipovi duž
  `rdfs:subClassOf`, učesnici duž `rdfs:subPropertyOf`), jer mašina ne zaključuje.
- `schema.ttl` kakav jeste, za nazive, komentare i prompt.
- `rules.json`: ime, dvojezično objašnjenje i tekst upita svakog pravila.

### Panel sa tragovima

„Tragovi“. Pri otvaranju učitava mašinu i podatke i izvršava svako pravilo. Prikazuje ime i
objašnjenje svakog pravila na jeziku pregledača, broj pogodaka i svaki pogodak kao rečenicu
sastavljenu od naziva čvorova („Siniša Mali je vlasnik Brigham Holding & Finance i držao je
funkciju u Gradu Beogradu kao gradonačelnik; preklapanje nepoznato“). Klik na pogodak otvara prikaz
putanje između dva aktera, ili ego prikaz osobe. Svaki unos nosi „trag za novinara, ne nalaz“.
Upit se ovde ne vidi.

### Pitaj

„Pitaj“. Polje za pitanje na srpskom ili engleskom. Stranica ne tumači pitanje; ona sastavlja
prompt i predaje ga posetiočevom asistentu.

1. Prompt sadrži: zadatak (napiši jedan SPARQL 1.1 `SELECT` upit nad ovim rečnikom); sažetak
   rečnika generisan iz `schema.ttl` na jeziku pregledača, svaku klasu i svojstvo sa nazivom i
   komentarom, i pojedince za uloge i tipove dokumenata; prefikse; jedno ili dva pravila kao
   rešene primere; konvencije (nepostojeći datum kraja znači nepoznato, nikad „i dalje“; datumi
   dolaze u tri preciznosti i porede se po godini; srodstvo, kumstvo i javna funkcija su veze;
   `rs:` imena); format odgovora (jedan ograđeni `sparql` blok, sa promenljivama za aktere koje
   treba prikazati); i pitanje.
2. Linkovi „Otvori u Claude-u“ i „Otvori u ChatGPT-u“ nose prompt unapred upisan u adresu, a
   „Kopiraj prompt“ je rezerva. Stranica sama ništa ne šalje.
3. Polje za lepljenje prima odgovor asistenta. Stranica izdvaja prvi ograđeni blok koda, ili ceo
   tekst kada ograde nema, izvršava ga istim izvršiocem kao Napredno, prikazuje redove kao
   rečenice i ističe aktere na mapi. „Prikaži upit“ otvara SPARQL; greška pri parsiranju ili
   izvršavanju prikazuje se rečima uz poruku mašine.
4. Ista rečenica upozorenja kao kod tragova, i napomena da pitanje ide asistentu koga je posetilac
   otvorio, a ne projektu.

### Napredno

„Napredno“, dostupno preko linka ispod odgovora i preko adrese `?upit=<base64url>`. Obično tekstualno
polje za lepljenje ili uređivanje SPARQL-a, „Izvrši“, pravila koja se učitavaju kao šabloni, tabela
rezultata sa „prikaži na mapi“ po redu, i „Podeli“, koje upisuje upit u adresu. Namenjeno novinarima
koji nauče obrasce, programerima i agentima koji generišu upite.

### Ograničenja

- Gornja granica od 500 redova i vremensko ograničenje od pet sekundi, svako sa čitljivom porukom;
  `LIMIT` se dodaje kada ga upit nema.
- Samo `SELECT`; `CONSTRUCT`, `ASK` i izmene se u interfejsu odbijaju.
- Ceo skup podataka je u pregledaču. Pri desetinama hiljada trojki Turtle je i dalje nekoliko
  megabajta; preispitati ako naraste preko toga.
- Vite traži poznato zaobilaženje za `process.env` kod Comunice; dokumentovano je u bildu.
- Odloženo učitani deo ostaje ispod 250 KB gzipovano, mereno u veb bildu.

### Inkrementi

Tanki, testom vođeni koraci, svaki se završava zelenim:

1. **Izvoz.** Gradle zadatak upisuje `data.ttl` sa zaključcima, `schema.ttl` i `rules.json`.
   Kotlin testovi: veza braće i sestara izlazi tipizirana kao porodična veza; roditelj izlazi kao
   učesnik; JSON nosi oba objašnjenja.
2. **Modul za upite.** `web/src/lib/query.ts`: učitavanje Turtle-a, izvršavanje, granica, vremensko
   ograničenje, redovi sa identifikatorima čvorova. Vitest: spoj, putanja svojstava, `SUBSTR`
   filter po godini, poruka o sintaksnoj grešci, prazan rezultat, granica.
3. **Ekvivalencija.** Vitest: svako pravilo nad objavljenim `data.ttl` daje tačno one vezane
   vrednosti koje je `checkRedFlags` u bildu upisao u `red-flags.json`.
4. **Panel sa tragovima.** Rečenice iz vezanih vrednosti, klik ka putanji ili egu, ključevi
   prevoda, stanja učitavanja i greške.
5. **Sastavljač prompta i lepljenje odgovora.** Prompt iz šeme na oba jezika (test: svaka klasa i
   svojstvo prisutni), linkovi i kopiranje, izdvajanje bloka koda (test: ograđen, neograđen, tekst
   pre i posle), izvršavanje i prikaz.
6. **Napredno.** Tekstualno polje, šabloni, adresa za deljenje, tabela rezultata.
7. **Dokumentacija i budžet.** Vodič za alate dobija zadatak; veličina paketa se proverava u veb
   bildu.

### Ne sada

- Direktan poziv modela sa posetiočevim sopstvenim ključem.
- MCP server nad skupom podataka za agente.
- `llms.txt` na sajtu, jeftino kada datoteke budu objavljene.

## Otvorena pitanja

- **Ćirilica i latinica.** Za sada odlučeno: svođenje u interfejsu transliteruje srpsku
  ćirilicu u latinicu, pa oba pisma nalaze istu oznaku. Imena stranog porekla čiji ćirilični
  zapis nije transliteracija slovo po slovo i dalje traže alias u podacima.
- **Veze sa mnogo učesnika.** Upravni odbor je jedna veza sa mnogo učesnika. Prikaz kao čvor
  je ispravan, ali se vizuelno razlikuje od parnih ivica. Potreban je vizuelni tretman.
- **Status.** Pregledač je objavljen kao sajt projekta; gornji inkrementi ostaju otvoreni.
- **Testiranje platna.** Iscrtavanje u Cytoscape-u je teško jedinično testirati. Izgradnju
  grafa (čvorovi, ivice, susedstvo, svođenje, stanje traga) treba držati u običnim TypeScript
  modulima sa Vitest testovima, a Svelte komponente ostaviti kao tanke ljuske za iscrtavanje.
