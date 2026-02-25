# Laevade pommitamine (Spring Boot)

Veebipõhine laevade pommitamise mäng: **kasutaja vs arvuti**.

## Reeglid

- Laud: **10×10**
- Laevastik: **1×4**, **2×3**, **3×2**, **4×1**
- Laevad **ei tohi kokku puutuda** servadega ega nurkadega (st ka diagonaalne kontakt on keelatud).

## Käivitamine

```bash
cd /home/japoia/git/battleship-springboot
mvn spring-boot:run
```

Seejärel ava brauseris `http://localhost:8080/`.

Kui port 8080 on hõivatud, käivita nt pordil 8081:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

## API (lühidalt)

- `POST /api/game/new` – uus mäng (arvuti laevad pannakse automaatselt)
- `GET /api/game/state` – seisu lugemine
- `POST /api/game/place` – mängija laeva paigutus (`{x,y,length,orientation}`)
- `POST /api/game/auto-place` – paiguta ülejäänud laevad automaatselt
- `POST /api/game/start` – alusta mängu (kui laevastik valmis)
- `POST /api/game/fire` – tulista arvuti lauda (`{x,y}`)

