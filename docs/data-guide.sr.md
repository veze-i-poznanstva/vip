# Vodič za podatke

*English version: [data-guide.md](data-guide.md)*

Kako se činjenice beleže u VIP-u i kako da dodate svoje. Nazad na [PROCITAJME](../PROCITAJME.md).

## Kako se beleže činjenice

Sedam pravila važi za svaki zapis. Validatori sprovode njihov oblik; da li izvor zaista kaže ono što zapis tvrdi može da proveri samo čovek koji ga čita.

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
  radnih uloga i opcionim datumima početka i kraja. Javna funkcija je takođe `:WorkConnection`:
  gradonačelnikova sa gradom (`rs:Grad_Beograd`, tipa `:GovernmentOrganization`), ministrova sa
  Vladom (`rs:Vlada_Srbije`), sa ulogom `vip:mayor` ili `vip:minister` i resorom u komentaru;
  pravilo „vlasnik na funkciji“ čita upravo njih. Školovanje je `:AlumniEducationalConnection`. Vlasništvo nad firmom je `vip:OwnershipConnection` sa opcionim
  udelom (decimalni procenat, od 0 do 100). Svaka veza nosi sopstvenu dokumentaciju, pa je dokaz
  za „X sedi u odboru Y“ odvojen od dokaza da X i Y postoje.
  Srodstvo nije izuzetak: braća i sestre su `vip:SiblingConnection`, a roditelj i dete
  `vip:ParentChildConnection` (`vip:parent`, `vip:child`), zabeleženi u
  `data/cases/porodicne-veze.ttl` sa sopstvenim izvorima. Oblici odbijaju gola porodična
  svojstva PoderVocab-a (`:hasSibling`, `:hasParent`) na osobi, jer ne ostavljaju mesta za
  dokaz.
- **Vreme je deo tvrdnje.** Veza nosi `:connectionStartDate` i `:connectionEndDate` onoliko
  precizno koliko izvor navodi: `xsd:gYear`, `xsd:gYearMonth` ili `xsd:date`, nikad izmišljeni
  1. januar. Datum koji nedostaje znači da je nepoznat, a ne da veza još traje. Uloge obavljane u
  različito vreme su zasebne veze. Oblici odbijaju kraj pre početka.
  Odnos ima jedan identitet, ali se piše kao jedan datiran zapis po stanju: Alo Media System je
  posedovao 100% Best Media Team od 24. decembra 2025, a 90% od februara 2026, pa postoje dva
  zapisa `vip:OwnershipConnection` između istih strana, prvi se završava u `2026-02` gde drugi
  počinje, a svaki ima sopstvene izvore. Ništa novo se ne deklariše; izgradnja izvodi odnos iz
  zapisa (ista vrsta veze, iste strane, iste uloge). Za svaku firmu, udeli zapisa koji mogu važiti
  istovremeno ne smeju zbirno prelaziti 100%; zapisi za koje se zna da slede jedan drugog ne
  sabiraju se, a dve nedatirane ili otvorene tvrdnje koje zajedno prelaze 100% ruše izgradnju dok
  se jedna ne datira ili ukloni. Pregledač spaja zapise jednog odnosa u jednu ivicu sa oznakom
  poslednjeg dokumentovanog stanja, a istoriju čuva u panelu sa detaljima; odnos čije poslednje
  stanje ima datum kraja crta se isprekidano. Kraj koji nedostaje je nepoznat, nikad „aktuelan“.
- **Svaki pojam ima oznake na engleskom, španskom i srpskom.** Svaka klasa, svojstvo i
  individua u rečniku i svaka SHACL poruka nose tekst sa oznakama `@en`, `@es` i `@sr`, tako da
  se korisnički interfejsi i izveštaji validacije mogu prikazati na bilo kom od tri jezika.
- **Isto ime, odvojeni zapisi.** Svaka osoba ima jedan URI i ime koje kaže o kome je reč, kao
  `Ivan Bošnjak (Zrenjanin)`. Osobe se razlikuju po javnim podacima: godini rođenja, funkciji,
  poslodavcu. `vip:wikipediaArticle` navodi članak o samom entitetu, a ne članak koji ga samo
  pominje. `vip:wikidataItem` je stavka entiteta na Vikidati kao IRI entiteta
  (`http://www.wikidata.org/entity/Q114870170`): trajni broj koji preživljava preimenovanje i
  važi za sva pisma i jezike, pa kaže o kom je imenjaku reč. To je identifikator, nikad izvor.
  Build ga upisuje iz članka na Vikipediji (`./gradlew :app:linkWikidata`), pa ga saradnici nikad
  ne traže; entitet bez članka identifikuje njegov VIP URI. Nikad ne beležite JMBG ni lični
  poreski broj: oblici odbijaju poreski broj na osobi, dok su matični broj i PIB firme javni
  podaci iz registra.
- **Tri prostora imena.** Pojmovi PoderVocab-a koriste prazan prefiks `:` za
  `http://poderopedia.com/vocab/`; sopstvene dopune rečnika koriste `vip:` za
  `https://w3id.org/vip/vocab#`; srpski podaci koriste `rs:` za `https://w3id.org/vip/rs/`, pa je
  `rs:Sinisa_Mali` osoba, a druge zemlje dobijaju svoj segment.

Rečnik je proširen tamo gde je PoderVocab imao praznine. Dopune su obeležene u `schema.ttl`
pod naslovom „VIP additions“: vlasnička veza i njena svojstva, veza kumstva (koju PoderVocab
nema, a u srpskom društvu je jaka kao srodstvo), porodične veze (braća i sestre, roditelj i dete), veza školskih drugova (ista škola, kada razred nije poznat), veza organizacije sa njenim finansijskim
informacijama, upravljačke radne uloge kao osnivač i predsednik upravnog odbora, vrste
dokumenata i statusi dokumenata, kao i dva javna identifikatora entiteta: članak na Vikipediji
i stavku na Vikidati.

## Rečnik

PoderVocab modeluje agente i veze među njima. Osobe i organizacije su `foaf:Person` i
`foaf:Organization`. Organizacije se dele na firme, političke, državne, nevladine, neprofitne,
međunarodne, verske i akademske. Veze su samostalni resursi sa sopstvenom taksonomijom:
prijateljstvo, isti razred, obrazovne veze, radne uloge, preuzimanja i spajanja firmi,
vlasništvo, članstvo, finansiranje, političke, emotivne i veze preko događaja u vestima, između
ostalog. Poreklo podataka nose `SourcedEntity`, `SourceReliability` i `Documentation`. Oznake i
komentari su na engleskom, španskom i srpskom.

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
  `alo-media-system.ttl` sadrži vlasništvo nad izdavačem tabloida Alo. Dva slučaja presecaju
  istrage: `javne-funkcije/` sadrži javne funkcije, po jedna datoteka za svaki državni organ
  (`vlada-srbije.ttl`, `grad-beograd.ttl`), a `porodicne-veze.ttl` srodstvo. Slučaj može biti takav
  folder kada bi jedna datoteka inače stalno rasla; provera rasporeda gleda samo folder najvišeg
  nivoa.
  Recenzent pročita jednu datoteku slučaja i vidi celu priču sa izvorima.

Radite sa AI agentom za programiranje? Nalepite prompt za brzi početak iz
[docs/agent-prompt.sr.md](agent-prompt.sr.md) kao prvu poruku: on objašnjava ceo put,
instalira alate, forkuje repozitorijum i vodi vas kroz dodavanje osoba i veza sa proverenim
izvorima, tražeći dozvolu za svaki korak, na srpskom. Donesite slučaj, ili ga pustite da izabere
srpskog političara i prati veze.

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
  `:hasDocumentType` i `:hasDocumentStatus vip:published`; dokument koji citira veza treba i
  `rdfs:comment` koji navodi odlomak što je potvrđuje (a ako je uklonjen, zašto).
- **Blokirani izvori.** Neki sajtovi (Istinomer, sajt SNS) odbijaju automatske klijente. Citirajte
  ih svejedno: arhiver zapisuje Wayback snimak, a validator prijavljuje upozorenje, ne grešku,
  sve dok snimak postoji.
- **Ograničenja arhiva.** Internet Archive usporava posle mnogo zahteva. Arhiver zadržava šta je
  uspeo, vraća 1 i traži ponovno pokretanje kasnije; već upisani zapisi ostaju. Blokada može da
  traje sat i duže, a ponavljanje tokom nje može da je produži, pa sačekajte 20 minuta, zatim duže,
  i napredak proveravajte sa `./gradlew :app:checkArchives`, koji nikad ne kontaktira arhiv. Takođe
  traži najviše 25 novih snimaka po pokretanju, a ostatak ispisuje za sledeće pokretanje, da jedna
  grana ne bi zatrpala arhiv. Pročitajte citirane adrese pre nego što pokrenete `verify` na tuđoj
  grani: arhiver će zatražiti od Wayback Machine-a da preuzme svaku od njih.
- **Mrtvi izvori.** Nikad ne brišite citat. Postavite `:hasDocumentStatus vip:removed` i dodajte
  `rdfs:comment` šta se desilo; validator ga tada ne računa kao dokaz, a arhiver ga preskače.
  Zamenite ga živim izvorom da entitet i dalje ima dva domena.
- **Brze provere.** `./gradlew :app:test :app:checkDataLayout :app:validateShacl` ne traži mrežu.
  `./gradlew :app:validateData` lokalno pokreće arhiver; sa promenljivom okruženja `CI`
  postavljenom na `true` samo proverava, kao na serveru.
