# Alati

*English version: [tools.md](tools.md)*

Validatori, pravila za crvene zastavice, pregledač i build. Nazad na [PROCITAJME](../PROCITAJME.md).

## Šta se nalazi u repozitorijumu

| Putanja | Uloga |
|---|---|
| `schema.ttl` | Ontologija PoderVocab 2.0 sa srpskim oznakama, plus VIP dopune i SHACL oblici |
| `data/entities/` | Po jedna Turtle datoteka za svaku osobu ili organizaciju: tip, imena, identifikatori, izvori identiteta |
| `data/cases/` | Po jedna Turtle datoteka za svaki istraživani slučaj: veze među entitetima, sa dokazima |
| `archives.ttl` | Snimak iz arhiva za svaki citirani dokument, održava ga build |
| `app/` | Kotlin alati za validaciju i konverziju |
| `rules/` | Obrasci crvenih zastavica kao SPARQL upiti, po jedan u datoteci |
| `web/` | Pregledač grafa u pretraživaču, objavljen na <https://veze-i-poznanstva.github.io/vip/> |

Modul `web/` prikazuje podatke čitaocima i objavljuje se kao sajt pri svakom zelenom push-u na
`main`. Nije deo lanca validacije; validatori u `app/` odlučuju šta se računa.

## Brzi početak

Potrebna je Java 17 ili novija. Gradle dolazi kroz wrapper.

```bash
# Validacija RDF sintakse
./gradlew :app:validate -Pfile=schema.ttl

# SHACL validacija podataka prema oblicima iz šeme
./gradlew :app:validateShacl -Pshapes=schema.ttl -Pdata=data

# Provera dobre prakse
./gradlew :app:checkBestPractices -Pfile=schema.ttl

# Provera OWL konzistentnosti
./gradlew :app:checkConsistency -Pfile=schema.ttl

# Metrike šeme
./gradlew :app:analyzeSchema -Pfile=schema.ttl

# Validacija verodostojnosti: dva domena po entitetu, svaki URL dostupan
./gradlew :app:validateVeracity -Pfile=data

# Raspored podataka: entiteti u entities/, veze u cases/, jedna matična datoteka po entitetu
./gradlew :app:checkDataLayout -Pdata=data

# Pravila za crvene zastavice nad podacima (tragovi za novinara; nikad ne pada)
./gradlew :app:checkRedFlags

# Sve validacije (zadaci za šemu podrazumevaju schema.ttl, zadaci za podatke data/)
./gradlew :app:validateAll
```

Da biste videli graf u pregledaču, izvezite podatke i pokrenite aplikaciju. Potrebna je samo
Java, jer build sam preuzima Node.js:

```bash
./gradlew :app:ttlToCytoscape   # osvežava podatke aplikacije iz data/
./gradlew :web:npmDev           # pokreće aplikaciju; zaustavlja se sa Ctrl+C
```

Zatim otvorite <http://localhost:5173/?lang=sr>, ili <http://localhost:5173/> za engleski. Ukucajte
ime u polje za pretragu, kliknite na čvor da ga stavite u središte, a dvostrukim klikom dodajte
njegove veze. Zadržite pokazivač na čvoru da pročitate njegov opis i otvorite članak na Vikipediji, ako postoji.

Alati za šemu uzimaju jednu datoteku (`-Pfile`, podrazumevano `schema.ttl`); alati za podatke
uzimaju stablo foldera sa Turtle datotekama ili jednu datoteku (`-Pdata`, podrazumevano `data`).
Svaki alat učitava pomoću Apache Jena, štampa izveštaj i vraća status 0 pri uspehu ili 1 pri
neuspehu, pa se može koristiti u skriptama i CI-ju. Izveštaj o metrikama šeme je informativan i
nikad ne pada.

## Validatori

### Validator verodostojnosti

Validator koji sprovodi pravilo o izvorima. Na osnovu podataka i rečnika pronalazi svakog
agenta i svaku vezu koje rečnik podrazumeva, prikuplja adrese iz njihovih čvorova Dokumentacija
(preskačući dokumente sa statusom uklonjen) i proverava:

1. Postoje najmanje dva URL-a i dolaze sa najmanje dva različita domena. Prefiks `www.` se
   ignoriše, pa se `www.istinomer.rs` i `istinomer.rs` računaju kao jedan domen.
2. Svaki URL je dostupan, uživo ili preko arhivirane kopije. Provera šalje HEAD zahtev, a ako
   ga sajt odbije, GET; prati do pet preusmerenja, uključujući relativna i ona između http i
   https, predstavlja se opisnim User-Agent zaglavljem i odustaje posle deset sekundi. Kada je
   stranica blokirana ili nestala, validator se oslanja na snimak zabeležen u `archives.ttl`, ili
   pita Wayback Machine da li snimak postoji. Stranica dostupna samo iz arhiva prijavljuje se kao
   upozorenje sa datumom snimka; stranica koju nema ni veb ni bilo koji arhiv je greška.

Entitet bez ikakve dokumentacije se prijavljuje kao greška, i tako se otkrivaju nedovoljno
potkrepljeni zapisi. Stranica je dostupna kad HEAD ili GET odgovori 2xx posle najviše pet
preusmeravanja; preusmeravanje na stranicu greške (putanja kao `/404.php` ili `/not-found`) ili
na početnu stranu sajta znači da stranice više nema. Preuzimaju se samo `http` i `https` adrese
na javnim hostovima, pa citirana adresa nikad ne može naterati build da ispituje privatnu mrežu.
Zapis o arhivi se prihvata samo ako je prava adresa snimka Wayback Machine; sve drugo je greška.
Svaka adresa se proverava jednom po pokretanju, ma koliko entiteta je citiralo.

Implementacija: `VeracityValidation` sadrži pravila, a `VeracityValidator` je ulazna tačka za
komandnu liniju. HTTP provera se ubacuje kao funkcija, tako da jedinični testovi rade bez mreže
preko zamene, a prava provera se koristi samo iz komandne linije.

Primer izlaza:

```
=== Veracity Validation Report ===

Checking: data
Vocabulary: schema.ttl
Archive records: archives.ttl
Rules:
  1. Every agent and every connection must have documentation with at least 2 URLs from different domains
  2. Every URL must be reachable, live or through an archived copy

Warnings (1):
  1. https://w3id.org/vip/rs/SNS: https://www.sns.org.rs/ not reachable live (HTTP 403); using archived copy from 2025-10-23 at http://web.archive.org/web/20251023082323/https://sns.org.rs/

✗ Validation FAILED

Errors found:
  1. https://w3id.org/vip/rs/Entity has only 1 URL
  2. https://w3id.org/vip/rs/Other has URLs from only 1 domain(s): istinomer.rs
  3. https://w3id.org/vip/rs/Third has inaccessible URLs: https://broken.url (HTTP 404, no archived copy)

Total errors: 3
```

(Izveštaji alata su na engleskom.)

### Arhiver izvora

Održava zapise o arhivi potpunim. Za svaku citiranu adresu koja nije označena kao uklonjena i nema zapis prvo pita Wayback
Machine za postojeći snimak, a tek onda traži novo snimanje preko Save Page Now, sa pauzom
između snimanja zbog ograničenja arhiva. Zapisi se upisuju u `archives.ttl`.

```bash
./gradlew :app:archiveSources   # lokalno: arhiviraj što nedostaje, pa komituj archives.ttl
./gradlew :app:checkArchives    # na CI: padni ako nešto nedostaje, bez kontakta sa arhivom
```

`verify` lokalno pokreće arhiver, a na CI proveru, pa doprinos koji citira novi izvor ne može
proći CI dok taj izvor nije arhiviran i zapis komitovan.

Dostupnost se proverava uživo samo za adrese u datotekama sa podacima promenjenim od osnove, što
je podrazumevano `origin/main`, pa opterećenje drugih sajtova ostaje malo. Drugu osnovu birate sa
`-PverifyBase=<ref>`, a proveru svih adresa sa `-PverifyFull`. Ako se osnova ne može pronaći,
proveravaju se sve adrese. Nedeljno pokretanje na CI-ju proverava sve adrese, jer izvori nestaju i
kada niko ne menja datoteku. Sve ostalo, uključujući pravilo dva domena, uvek pokriva ceo skup
podataka.

### Provera rasporeda podataka

Čuva doslednost rasporeda podataka. Osobe i organizacije se deklarišu, tj. dobijaju svoj tip,
samo u `data/entities/`, po jedna u datoteci, a datoteka nosi ime po URI-ju entiteta, pa
`rs:Sinisa_Mali` živi u `sinisa-mali.ttl`. Veze se deklarišu samo u `data/cases/`. Svaki entitet
ima tačno jednu matičnu datoteku, a sve na šta se datoteka poziva mora biti negde deklarisano:
učesnici u nekoj datoteci, tipovi i svojstva u rečniku. Pogrešno otkucan URI zato ne može da
stvori učesnika-duha, pogrešno napisana klasa se ne prećutkuje, a izmišljen odnos između dva
entiteta se odbija uz uputstvo da se koristi veza sa dokazima. Datoteke sa podacima ne smeju da
sadrže iskaze rečnika (potklasa, domen, opseg, OWL, SHACL), jer bi ti iskazi menjali značenje
svake druge provere. Turtle datoteke bilo gde drugde u `data/` se prijavljuju.

### RDF validator

Parsira datoteku i prijavljuje sintaksne greške, broj tripleta i prostore imena.

### SHACL validator

Proverava podatke prema SHACL oblicima iz `schema.ttl`: PersonShape, OrganizationShape,
ConnectionShape, DocumentationShape i FinancialInformationShape. Podaci se proveravaju zajedno
sa onim što rečnik podrazumeva (potklase, potsvojstva, domeni i opsezi), pa `foaf:Person`
zadovoljava ograničenje koje traži `foaf:Agent` bez ponavljanja hijerarhije u podacima.
Svaki dokument uz vezu mora u `rdfs:comment` citirati odlomak koji je potvrđuje. Prijavljuje svako
kršenje sa čvorom na koji se odnosi i porukom.

### Provera OWL konzistentnosti

Pokreće rezoner nad ontologijom da bi pronašao logičke nekonzistentnosti, nezadovoljive klase i
kružne hijerarhije.

### Provera dobre prakse

Prijavljuje nedostajuće oznake i komentare, kršenja konvencija imenovanja, klase bez roditelja,
nedoslednosti jezičkih oznaka i zastarele elemente.

### Metrike šeme

Broji klase, svojstva i individue, meri dubinu hijerarhije i prijavljuje metrike složenosti kao
što su restrikcije i aksiome disjunktnosti.

## Pravila za crvene zastavice

Obrasci koji zaslužuju drugi pogled novinara zapisani su kao SPARQL upiti u `rules/`, po jedna
datoteka za svaki obrazac, sa dvojezičnim objašnjenjem na vrhu svake datoteke:

| Pravilo | Obrazac |
|---|---|
| `nepotism.rq` | Supružnik, partner, kum ili član porodice osobe koja vodi organizaciju radi u njoj |
| `owner-in-office.rq` | Osoba koja je vlasnik firme istovremeno ima položaj u državnoj organizaciji |
| `schoolmates-in-power.rq` | Dve osobe iz iste škole obe su, kroz posao ili politiku, vezane za istu organizaciju |

```bash
./gradlew :app:checkRedFlags     # pokreće svako pravilo nad data/ i ispisuje pogotke
```

Datumi odlučuju šta se prijavljuje. Slučaj čiji datumi pokazuju da se dva perioda nikad nisu
dodirnula se izostavlja. Ostali nose status: `overlapping` kada su sva četiri datuma zabeležena i
godine se dodiruju, `unknown` kada neki nedostaje. Većina tragova je `unknown` dok podaci nemaju
datume, pa ni prazan rezultat ne govori mnogo.

Pokretač učitava podatke zajedno sa onim što rečnik povlači, pa pravilo napisano nad
`bio:participant` vidi svako specijalizovano svojstvo učesnika. Pogoci su tragovi, ne zaključci:
zadatak ih ispisuje i uvek vraća 0. Novo pravilo se dodaje kao `.rq` datoteka u `rules/` čiji prvi
redovi komentara objašnjavaju pravilo na engleskom i srpskom; jedinični testovi pokreću svako
pravilo nad primerom koji mora da se poklopi i jednim koji ne sme.

## Pregledač grafa

Modul `web/` je Svelte aplikacija koja prikazuje podatke kao Cytoscape graf. Osobe, firme,
stranke i akademske ustanove su čvorovi različitih oblika, a veze su označene ivice. Podaci se
konvertuju komandom:

```bash
./gradlew :app:ttlToCytoscape -Pinput=data -Poutput=web/public/test-data-cytoscape.json -Pschema=schema.ttl
```

Konverter vodi rečnik: agenti postaju čvorovi, svaka veza postaje ivica između svakog para
svojih učesnika, označena sopstvenom oznakom veze, njenim radnim ulogama ili vrstom, a direktna
svojstva između agenata postaju ivice označene svojstvom. Dokumentacija i goli URL-ovi se
nikad ne pojavljuju u grafu.

Zatim se pregledač pokreće sa `npm install` i `npm run dev` u folderu `web/`, ili kroz Gradle
sa `./gradlew :web:npmDev`. Pregledač govori engleski i srpski; jezik se bira prekidačem u
zaglavlju ili sa `?lang=sr` u adresi. Logika je testirana; samo crtanje grafa nije.

## Jezici

Sve što čitalac sreće postoji na engleskom i na srpskom, latinicom:

- **Dokumentacija.** `README.md` ima par `PROCITAJME.md`; svaki drugi dokument je
  `docs/<ime>.md` sa parom `docs/<ime>.sr.md`. Jedinični test obara build kada prevod nedostaje
  ili kada mu se struktura naslova razlikuje od originala, pa dve verzije ne mogu neprimetno da
  se razdvoje. Datoteke sa podacima nose zaglavlje na oba jezika. Tekst licence ostaje na
  izvornom engleskom.
- **Rečnik.** Svaki pojam i svaka SHACL poruka u `schema.ttl` nose tekst sa oznakama `@en`, `@es`
  i `@sr`, kako je opisano u odeljku „Kako se beleže činjenice“.
- **Pregledač.** Tekst interfejsa stoji u `web/src/locales/<kod>.json`, po jedna datoteka za
  svaki jezik sa istim ključevima; Vitest test pada kada ključ nedostaje u bilo kom jeziku. Jezik
  se uzima iz `?lang=` u adresi, zatim iz poslednjeg izbora u tom pregledaču, zatim iz jezika samog
  pregledača. Novi jezik se dodaje njegovim rečnikom i njegovim kodom u `supportedLanguages` u
  `web/src/lib/i18n.ts`, i prevedenim dokumentima pored originala.

Još nije lokalizovano: izveštaji alata komandne linije, koji su na engleskom, i oznake veza koje
konverter upisuje u podatke za pregledač, koje uzimaju englesku oznaku iz rečnika.

## Automatizacija izgradnje

Sve što uslovljava objavljivanje pokreće se jednim Gradle zadatkom:

```bash
./gradlew verify
```

On izvršava jedinične testove, sve provere šeme (`:app:validateSchema`), sve provere podataka
(`:app:validateData`: raspored, SHACL, zapisi o arhivi i verodostojnost), pravila za crvene
zastavice, izvoz podataka u Cytoscape JSON i izgradnju pregledača nad tim svežim izvozom. Samo dva redosleda su bitna i
oba su obezbeđena: arhiviranje pre verodostojnosti, izvoz pre izgradnje pregledača; ostalo može
ići paralelno. Ako bilo koji korak padne, pada i ceo lanac,
pa crven `verify` znači da podaci ili kod nisu spremni za objavljivanje.

Kontinuirana integracija je namerno tanka: tok u `.github/workflows/verify.yml` instalira Javu
i poziva `./gradlew verify`. Sva logika pripada build-u, nikad CI konfiguraciji, tako da ista
komanda daje isti odgovor na laptopu i na serveru.

Manji ulazi za svakodnevni rad:

```bash
./gradlew :app:test            # samo jedinični testovi, bez mreže
./gradlew :app:validateSchema  # sintaksa, OWL konzistentnost, dobra praksa, metrike
./gradlew :app:validateData    # raspored podataka, SHACL oblici, zapisi o arhivi, verodostojnost
./gradlew :web:npmDev          # lokalno pokretanje pregledača
```

Ključne biblioteke: Apache Jena 4.10.0 (RDF, RDFS i OWL rezonovanje, SHACL), Gson 2.10.1, Kotlin 1.9.21.

## Plan: istraživač grafa

Pregledač postaje istraživač vođen pretragom: ukucate ime, vidite osobu i njene neposredne
veze, klikom proširite čvor i pratite trag kroz graf, a izvori svake tvrdnje su na jedan klik.
Dizajn je opisan u [docs/ui-design.sr.md](ui-design.sr.md). Do sada izgrađeno: polje za
pretragu sa predlozima (poklapanje ne gleda velika slova, dijakritike ni pismo, pa „brnabic“ i
„Брнабић“ oba nalaze Anu Brnabić), prikaz jednog skoka oko izabranog čvora i dvostruki klik koji
dodaje veze čvora. Logika grafa stoji u `web/src/lib/graph.ts` sa Vitest testovima; Svelte
komponente ostaju tanke. Preostaje: izvori i aliasi u izvozu, panel sa detaljima i izvorima,
ponovno centriranje i trag, dubina dva, trajni linkovi.
