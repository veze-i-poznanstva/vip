# VIP: Mapiranje mreža moći uz proverene izvore

*English version: [README.md](README.md)*

## Svrha

VIP je građanska inicijativa. Cilj je da se izgradi otvoren, mašinski proverljiv graf znanja o
vezama između uticajnih ljudi: političara, funkcionera, privrednika, i organizacija, firmi i
stranaka sa kojima su povezani. Graf treba da pomogne krivičnim i novinarskim istragama tako
što veze čini vidljivim, proverljivim i dokumentovanim, umesto da ostanu rasute po arhivama
medija i registrima.

Tri načela vode dizajn:

1. **Svaka tvrdnja ima izvor.** Entitet ili veza ulaze u graf samo ako su potkrepljeni sa
   najmanje dva javna dokumenta sa dva različita domena, i ti dokumenti moraju biti dostupni.
   Ovo pravilo automatski sprovodi validator verodostojnosti.
2. **Podaci su strukturirani, ne tekst.** Činjenice se izražavaju u RDF-u pomoću rečnika
   PoderVocab, napravljenog za Poderopediju. Korišćenje postojećeg rečnika čini podatke
   uporedivim sa drugim istraživačkim skupovima podataka.
3. **Podaci se proveravaju pre nego što im se veruje.** Alati proveravaju sintaksu, logičku
   konzistentnost, SHACL ograničenja i dobru praksu modelovanja, tako da se greške otkriju pri
   unosu, a ne tokom istrage.

Projekat je inspirisan [Poderopedijom](https://www.poderopedia.org/), čileanskom platformom
koja je prva mapirala mreže moći za potrebe istraživačkog novinarstva.

Primer podataka u `data/`, po jedna Turtle datoteka za svaku osobu ili organizaciju i po jedna za
svaki istraživani slučaj, opisuje srpske javne ličnosti i njihove zajedničke škole,
stranke i poslovne veze, sa izvorima iz srpskih fact-checking i istraživačkih medija kao što su
Istinomer, KRIK, Cenzolovka i Media Ownership Monitor.

## Kako se beleže činjenice

Pet pravila važe za svaki zapis. Validatori ih sprovode.

- **Dokaz je čvor Dokumentacija, nikad goli URL.** Svaki izvor je resurs `:Documentation` sa
  svojom adresom, vrstom dokumenta (novinski članak, enciklopedijski članak, zapis iz registra,
  zvanični dokument, profil na sajtu za proveru činjenica, veb stranica, video snimak) i
  statusom (objavljen, arhiviran, uklonjen). Vezuje se za osobu, organizaciju ili vezu koju
  potkrepljuje preko `:hasDocumentation`. Izvor koji više ne postoji zadržava svoj zapis sa
  statusom uklonjen, tako da se istorija čuva, ali se više ne računa kao dokaz.
- **Svaki izvor ima arhiviranu kopiju.** Stranice se brišu ili tiho menjaju pod pritiskom, pa se
  svaka citirana adresa snima u Wayback Machine Internet arhiva, a adresa i datum snimka beleže
  se u `archives.ttl`, po adresi dokumenta. Tu datoteku održava build, nikad ruka. Adresa snimka
  sadrži vreme snimanja, pa je ona stabilna referenca za prikaz i proveru; izvorna adresa ostaje
  identitet citata.
- **Odnosi su resursi veze, nikad gola svojstva.** Članstvo u stranci je `:PoliticalConnection`
  između osobe i stranke. Zaposlenje i mesta u odborima su `:WorkConnection` sa jednom ili više
  radnih uloga i opcionim datumima početka i kraja. Školovanje je
  `:AlumniEducationalConnection`. Vlasništvo nad firmom je `vip:OwnershipConnection` sa opcionim
  udelom. Svaka veza nosi sopstvenu dokumentaciju, pa je dokaz za „X sedi u odboru Y“ odvojen
  od dokaza da X i Y postoje.
- **Svaki pojam ima oznake na engleskom, španskom i srpskom.** Svaka klasa, svojstvo i
  individua u rečniku i svaka SHACL poruka nose tekst sa oznakama `@en`, `@es` i `@sr`, tako da
  se korisnički interfejsi i izveštaji validacije mogu prikazati na bilo kom od tri jezika.
- **Tri prostora imena.** Pojmovi PoderVocab-a koriste prazan prefiks `:` za
  `http://poderopedia.com/vocab/`; sopstvene dopune rečnika koriste `vip:` za
  `https://w3id.org/vip/vocab#`; srpski podaci koriste `rs:` za `https://w3id.org/vip/rs/`, pa je
  `rs:Sinisa_Mali` osoba, a druge zemlje dobijaju svoj segment.

Rečnik je proširen tamo gde je PoderVocab imao praznine. Dopune su obeležene u `schema.ttl`
pod naslovom „VIP additions“: vlasnička veza i njena svojstva, veza kumstva (koju PoderVocab
nema, a u srpskom društvu je jaka kao srodstvo), veza organizacije sa njenim finansijskim
informacijama, upravljačke radne uloge kao osnivač i predsednik upravnog odbora, vrste
dokumenata i statusi dokumenata.

## Šta se nalazi u repozitorijumu

| Putanja | Uloga |
|---|---|
| `schema.ttl` | Ontologija PoderVocab 2.0 sa srpskim oznakama, plus VIP dopune i SHACL oblici |
| `data/entities/` | Po jedna Turtle datoteka za svaku osobu ili organizaciju: tip, imena, identifikatori, izvori identiteta |
| `data/cases/` | Po jedna Turtle datoteka za svaki istraživani slučaj: veze među entitetima, sa dokazima |
| `archives.ttl` | Snimak iz arhiva za svaki citirani dokument, održava ga build |
| `app/` | Kotlin alati za validaciju i konverziju |
| `rules/` | Obrasci crvenih zastavica kao SPARQL upiti, po jedan u datoteci |
| `web/` | Pregledač grafa u pretraživaču, **samo demo** |

Modul `web/` postoji da vizuelno pokaže podatke. Nije deo lanca validacije i ne daje nikakve
garancije. Proizvod su validatori u `app/`.

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
Prijavljuje svako kršenje sa čvorom na koji se odnosi i porukom.

### Provera OWL konzistentnosti

Pokreće rezoner nad ontologijom da bi pronašao logičke nekonzistentnosti, nezadovoljive klase i
kružne hijerarhije.

### Provera dobre prakse

Prijavljuje nedostajuće oznake i komentare, kršenja konvencija imenovanja, klase bez roditelja,
nedoslednosti jezičkih oznaka i zastarele elemente.

### Metrike šeme

Broji klase, svojstva i individue, meri dubinu hijerarhije i prijavljuje metrike složenosti kao
što su restrikcije i aksiome disjunktnosti.

## Rečnik

PoderVocab modeluje agente i veze među njima. Osobe i organizacije su `foaf:Person` i
`foaf:Organization`. Organizacije se dele na firme, političke, državne, nevladine, neprofitne,
međunarodne, verske i akademske. Veze su samostalni resursi sa sopstvenom taksonomijom:
prijateljstvo, školski drugovi, obrazovne veze, radne uloge, preuzimanja i spajanja firmi,
vlasništvo, članstvo, finansiranje, političke, emotivne i veze preko događaja u vestima, između
ostalog. Poreklo podataka nose `SourcedEntity`, `SourceReliability` i `Documentation`. Oznake i
komentari su na engleskom, španskom i srpskom.

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

Pokretač učitava podatke zajedno sa onim što rečnik povlači, pa pravilo napisano nad
`bio:participant` vidi svako specijalizovano svojstvo učesnika. Pogoci su tragovi, ne zaključci:
zadatak ih ispisuje i uvek vraća 0. Novo pravilo se dodaje kao `.rq` datoteka u `rules/` čiji prvi
redovi komentara objašnjavaju pravilo na engleskom i srpskom; jedinični testovi pokreću svako
pravilo nad primerom koji mora da se poklopi i jednim koji ne sme.

## Pregledač grafa (demo)

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
zaglavlju ili sa `?lang=sr` u adresi. Pregledač služi da pokaže kako podaci izgledaju. Crtanje
grafa nije testirano i pregledač nije namenjen za produkciju.

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
zastavice, izvoz podataka u Cytoscape JSON i izgradnju demo pregledača nad tim svežim izvozom. Samo dva redosleda su bitna i
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
./gradlew :web:npmDev          # lokalno pokretanje demo pregledača
```

Ključne biblioteke: Apache Jena 4.10.0 (RDF, RDFS i OWL rezonovanje, SHACL), Gson 2.10.1, Kotlin 1.9.21.

## Plan: istraživač grafa

Pregledač postaje istraživač vođen pretragom: ukucate ime, vidite osobu i njene neposredne
veze, klikom proširite čvor i pratite trag kroz graf, a izvori svake tvrdnje su na jedan klik.
Dizajn je opisan u [docs/ui-design.sr.md](docs/ui-design.sr.md). Do sada izgrađeno: polje za
pretragu sa predlozima (poklapanje ne gleda velika slova, dijakritike ni pismo, pa „brnabic“ i
„Брнабић“ oba nalaze Anu Brnabić), prikaz jednog skoka oko izabranog čvora i dvostruki klik koji
dodaje veze čvora. Logika grafa stoji u `web/src/lib/graph.ts` sa Vitest testovima; Svelte
komponente ostaju tanke. Preostaje: izvori i aliasi u izvozu, panel sa detaljima i izvorima,
ponovno centriranje i trag, dubina dva, trajni linkovi.

## Licenca

SPDX-License-Identifier: LicenseRef-DACL-0.1

Kod, primer podataka i dokumentacija napisani za ovaj projekat objavljeni su pod licencom
[Degrowth-Aligned Commons License (DACL) 0.1](LICENSE.md) ili bilo kojom kasnijom verzijom koju
objave njeni staratelji na [codeberg.org/degrowth-boundary-commons/dacl](https://codeberg.org/degrowth-boundary-commons/dacl).
DACL je licenca dostupnog izvornog koda sa obavezom deljenja pod istim uslovima i sa
ograničenjima namene. Nije licenca koju je odobrio OSI. Pročitajte licencu pre ponovne upotrebe.

Obim: `schema.ttl` je revizija rečnika PoderVocab koji je napravila Poderopedija. To nije delo
ovog projekta i ovde se ne licencira iznova; ostaje pod uslovima koje su izabrali njegovi
autori. Sopstvene dopune šeme, pojmove `vip:`, SHACL oblike i srpske oznake, ovaj projekat nudi
pod DACL licencom.

## Doprinos podacima

Podaci žive u `data/` u dve vrste datoteka:

- `data/entities/<slug>.ttl`, po jedna za svaku osobu ili organizaciju: tip, imena,
  identifikatori, kratak opis i izvori koji potvrđuju ko je ili šta je. Datoteka nosi ime po
  URI-ju, pa je `rs:Sinisa_Mali` u `sinisa-mali.ttl`. Mnoge od ovih datoteka imaju deset redova i
  to je u redu: datoteka je i dalje jedino mesto gde se gleda, a kad se o entitetu kasnije sazna
  više, menja se samo ona.
- `data/cases/<slucaj>.ttl`, po jedna za svaki istraživani slučaj: veze među entitetima, svaka sa
  datumima, ulogama i dokazima. `milenijum-tim.ttl` sadrži vlasničke i radne veze građevinske
  grupe; `peta-beogradska-gimnazija.ttl` sadrži razred koji je stigao do vlade;
  `alo-media-system.ttl` sadrži vlasništvo nad izdavačem tabloida Alo. Recenzent pročita jednu
  datoteku slučaja i vidi celu priču sa izvorima.

Radite sa AI agentom za programiranje? Nalepite prompt iz
[docs/agent-prompt.sr.md](docs/agent-prompt.sr.md) kao prvu poruku: on instalira alate, forkuje
repozitorijum i vodi vas kroz dodavanje slučaja sa proverenim izvorima, tražeći dozvolu za svaki
korak, na srpskom.

Kako doprineti slučaj:

1. Za svaku osobu ili organizaciju koje još nema u `data/entities/` dodajte tamo datoteku sa
   istim prefiksima kao postojeće i uvodnim komentarom. Na entitete koji već postoje pozivajte se
   URI-jem iz svog slučaja, nikad ih ne deklarišite ponovo.
2. Napravite `data/cases/<slucaj>.ttl` sa uvodnim komentarom, na srpskom i engleskom, koji kaže
   šta slučaj obuhvata, i u njoj deklarišite veze.
3. Svakom novom entitetu i svakoj vezi dajte dokaze kao `:Documentation` čvorove: najmanje dva
   dokumenta sa dva različita domena, oba dostupna. Prednost imaju primarni izvori: zvanični
   registri, sudski spisi, objavljene istrage i arhive fact-checking redakcija. Kratak
   `rdfs:comment` na dokumentu koji kaže šta dokument potkrepljuje pomaže recenzentu.
4. Pokrenite `./gradlew verify`. Lokalno to ujedno arhivira svaku novocitiranu stranicu preko
   Wayback Machine i ažurira `archives.ttl`; komitujte tu datoteku zajedno sa podacima. Na CI
   isti korak samo proverava, pa nearhiviran izvor obara build.
5. Predložite nove datoteke entiteta, datoteku slučaja i `archives.ttl` zajedno.

Build sve ovo sprovodi: provera rasporeda podataka, SHACL oblici, zapisi o arhivi i validator
verodostojnosti.

Praktične napomene:

- **Prefiksi.** Prekopirajte blok prefiksa iz bilo koje postojeće datoteke. URI-jevi podataka su
  `rs:` plus ime latinicom sa donjim crtama, `rs:Sinisa_Mali`, `rs:Milenijum_Tim`; veze se zovu po
  svojim krajevima, `rs:SinisaMali_SNS`. Datoteka entiteta je lokalno ime malim slovima sa
  crticama, `sinisa-mali.ttl`.
- **Najmanje što treba.** Osobi trebaju `foaf:firstName` i `:firstLastName`; organizaciji
  `foaf:name`; vezi učesnici i dokumentacija. Dokumentu trebaju `:hasDocumentURL`,
  `:hasDocumentType` i `:hasDocumentStatus vip:published`.
- **Blokirani izvori.** Neki sajtovi (Istinomer, sajt SNS) odbijaju automatske klijente. Citirajte
  ih svejedno: arhiver zapisuje Wayback snimak, a validator prijavljuje upozorenje, ne grešku,
  sve dok snimak postoji.
- **Ograničenja arhiva.** Internet Archive usporava posle mnogo zahteva. Arhiver zadržava šta je
  uspeo, vraća 1 i traži ponovno pokretanje za nekoliko minuta; već upisani zapisi ostaju. Takođe
  traži najviše 25 novih snimaka po pokretanju, a ostatak ispisuje za sledeće pokretanje, da jedna
  grana ne bi zatrpala arhiv. Pročitajte citirane adrese pre nego što pokrenete `verify` na tuđoj
  grani: arhiver će zatražiti od Wayback Machine-a da preuzme svaku od njih.
- **Mrtvi izvori.** Nikad ne brišite citat. Postavite `:hasDocumentStatus vip:removed` i dodajte
  `rdfs:comment` šta se desilo; validator ga tada ne računa kao dokaz, a arhiver ga preskače.
  Zamenite ga živim izvorom da entitet i dalje ima dva domena.
- **Brze provere.** `./gradlew :app:test :app:checkDataLayout :app:validateShacl` ne traži mrežu.
  `./gradlew :app:validateData` lokalno pokreće arhiver; sa promenljivom okruženja `CI`
  postavljenom na `true` samo proverava, kao na serveru.
