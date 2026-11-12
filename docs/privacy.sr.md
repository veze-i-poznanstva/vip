# Privatnost

*English version: [privacy.md](privacy.md)*

Šta pregledač radi sa vašim podacima, a to je gotovo ništa, i šta skup podataka radi sa podacima
osoba koje su u njemu. Nazad na [PROCITAJME](../PROCITAJME.md).

## U vašem pregledaču

Pregledač na veze-i-poznanstva.github.io/vip radi u celosti u vašem veb-pregledaču. Preuzima graf,
zapise i pravila sa istog sajta, a sve ostalo, uključujući svaki upit, radi na vašem uređaju. Nema
našeg servera, nema naloga, nema analitike, nema kolačića, niti fontova i skripti koji se učitavaju
od bilo koga drugog.

U vašem pregledaču se čuva jedna stvar: jezik koji izaberete, pod ključem `lang`, upisan samo kada
izaberete jezik i pročitan pri sledećoj poseti. Brisanje podataka sajta u pregledaču ga uklanja.

Kada podelite upit iz Naprednog, tekst upita putuje u linku koji kopirate, i nigde drugde.

## Hosting

Sajt hostuje GitHub preko usluge GitHub Pages. GitHub beleži IP adresu svake posete iz
bezbednosnih razloga, bez obzira na to da li je posetilac prijavljen na GitHub, i te zapise drži
pod [GitHubovom izjavom o privatnosti](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
Mi ih nikada ne vidimo. Videti
[GitHub Pages: prikupljanje podataka](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages#data-collection).

## Kada kliknete napolje

Ništa ne napušta vaš pregledač dok ne kliknete na link ili negde ne nalepite tekst. Tada sajt na
koji stignete vidi posetu, pod sopstvenim uslovima:

- **Otvori u Claude** i **Otvori u ChatGPT** (panel Pitaj) otvaraju asistenta sa vašim pitanjem i
  instrukcijom da pročita objavljeno uputstvo. Vaše pitanje odlazi Anthropicu ili OpenAI-ju tek
  tada, pod vašim nalogom kod njih i njihovim uslovima. **Kopiraj upit za asistenta** drži sve na
  vašem uređaju dok ga ne nalepite.
- **Pošalji prijavu** (Informacija nije tačna?) otvara unapred popunjenu prijavu na GitHubu.
  Slanjem se prijava objavljuje pod vašim GitHub nalogom. **Popravite sami uz pomoć AI** samo
  kopira prompt.
- Linkovi ka izvorima, arhiviranim kopijama, Vikipediji i repozitorijumu vode na te sajtove.

## Osobe u podacima

VIP objavljuje lične podatke: imena osoba na javnim položajima i njihove dokumentovane veze sa
organizacijama i međusobno, sa datumima i izvorima. To je svrha skupa podataka: informacije od
javnog interesa o ljudima koji drže javnu funkciju, vode stranke, poseduju firme koje posluju sa
državom ili stoje u njihovom najužem krugu, sabrane iz onoga što su ugledni mediji i zvanični
registri već objavili. [Zašto mapirati veze](why-connections.sr.md) kaže zašto, a [vodič za
podatke](data-guide.sr.md) kako.

Pravila kojih se držimo, ko god da je osoba:

- Svaki entitet i svaka veza navode najmanje dva javna izvora sa dva različita domena, svaki sa
  arhiviranom kopijom, i citiraju deo na koji se oslanjaju.
- Beležimo ono što izvor kaže, uz navođenje izvora, i ništa više. Veza je trag, ne optužba; zapisi
  beleže veze, ne krivicu.
- Ne beležimo JMBG, lični poreski broj, kućnu adresu ni privatne kontakte. Matični broj firme je
  javni podatak iz registra.
- Stranačka pripadnost se beleži samo kada ju je osoba sama učinila javnom, kandidovanjem za
  stranku ili funkcijom u njoj.
- Veza koja je u izvorima okončana označava se kao okončana; nepostojeći datum kraja znači
  nepoznato, nikad „i dalje“.

## Ispravke i istorija

Ako ste u podacima i zapis je pogrešan, upotrebite **Informacija nije tačna?** na stranici. Zapis
koji pogrešno prenosi svoj izvor ili meša dve istoimene osobe se ispravlja. Zapis koji kaže ono
što njegovi izvori kažu mi ne menjamo: izvor prvo mora da ispravi onaj ko ga je objavio, redakcija
ili registar, a VIP ga prati. Kada se ugledni izvori međusobno ne slažu, VIP ne bira među njima;
sporna tvrdnja se izostavlja. Citat za koji se utvrdi da je pogrešan briše se iz datoteke;
istorija repozitorijuma čuva ranije verzije svake datoteke, a komit koji ga briše kaže zašto. Oba
puta za prijavu su javna.

*Poslednja izmena: 11. novembar 2026.*
