# GitFlow

## Regole di sviluppo

- Ogni issue deve essere sviluppata in un branch dedicato.
- Creare il branch partendo da `main` aggiornato.
- Non sviluppare direttamente su `main`.
- Al termine dello sviluppo aprire una Pull Request verso il branch principale del repository.
- La Pull Request deve riferire l'issue sviluppata, preferibilmente usando la sintassi GitHub `Closes #<issue-number>` quando la PR completa il lavoro.
- Non fare merge di Pull Request con test o check falliti.
- Dopo il merge, eliminare il branch remoto.

## Naming dei branch

Usare un prefisso diverso in base alla natura dell'issue:

- Feature: `feature/<issue-number>-<issue-name>`
- Bug fix: `bug/<issue-number>-<issue-name>`
- Documentazione: `docs/<issue-number>-<issue-name>`
- Manutenzione/configurazione: `chore/<issue-number>-<issue-name>`
- Refactoring senza cambio funzionale: `refactor/<issue-number>-<issue-name>`

Esempi:

- `feature/42-add-market-calendar-api`
- `bug/57-fix-stale-price-detection`
- `docs/61-document-gitflow-rules`
- `chore/62-add-ci-pipeline`
- `refactor/63-extract-ingestion-service`

Note:

- Il nome dell'issue nel branch deve essere trasformato in slug: lettere minuscole, parole separate da `-`, senza spazi.
- Evitare caratteri speciali, accenti e punteggiatura nel nome del branch.
- Non usare uno slash iniziale: usare `feature/...` e `bug/...`, non `/feature/...` o `/bug/...`.

## Pull Request

Ogni Pull Request deve contenere una breve descrizione del lavoro svolto.

La descrizione deve includere, quando rilevante:

- issue collegata, usando `Closes #<issue-number>` se la PR completa il task;
- sintesi delle attività svolte;
- test o verifiche eseguite;
- eventuali modifiche a configurazione, schema dati, API o documentazione;
- eventuali decisioni, limitazioni o follow-up emersi durante lo sviluppo.

Template consigliato:

```md
Closes #<issue-number>

## Attività svolte

- ...

## Verifiche

- ...

## Note

- ...
```

## Commenti su issue e PR

- La sintesi tecnica del lavoro svolto deve stare principalmente nella Pull Request, perché è collegata direttamente al diff, alla review e al merge.
- Non duplicare automaticamente sulla issue lo stesso contenuto già presente nella Pull Request.
- Aggiungere un commento alla issue solo quando aggiunge valore, ad esempio:
  - lo sviluppo cambia lo scope originario dell'issue;
  - emergono decisioni importanti non ovvie dalla PR;
  - il task viene completato solo parzialmente;
  - serve lasciare un handoff o una nota operativa prima della PR;
  - vengono creati follow-up o nuove issue collegate.
- Se la PR completa l'issue, usare `Closes #<issue-number>` nella PR invece di aggiungere un commento ridondante sulla issue.

## Strategia di merge

- Preferire lo squash merge per mantenere la history di `main` leggibile.
- Il titolo del commit generato dallo squash deve essere chiaro e riferibile all'issue, ad esempio: `Add market calendar API (#42)`.
- Evitare merge commit rumorosi salvo necessità specifiche.

## Commit

- Usare messaggi di commit chiari e descrittivi.
- I commit intermedi possono essere granulari, ma la Pull Request deve raccontare il cambiamento in modo comprensibile.
- Non committare segreti, file locali, build output o dati generati.

## Protezione del branch principale

Quando configurabile su GitHub, proteggere `main` con queste regole:

- push diretto disabilitato;
- Pull Request obbligatoria;
- check/test verdi obbligatori;
- eliminazione automatica o manuale del branch dopo merge.
