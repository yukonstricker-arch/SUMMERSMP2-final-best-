# SummerSMPCore

A small custom plugin for a Paper **26.1.2** Lifesteal server. It does four things:

1. **No End Crystals** — the crafting recipe is removed and placing a crystal is blocked.
2. **3-Mace cap** — once 3 Heavy Cores have been picked up anywhere on the server, no more can be found (the 4th+ crumbles to dust). The number is configurable.
3. **Ender chest restriction** — ender chests work normally, but Heavy Cores and Maces can't be put inside them.
4. **/ban** — a staff-only temp-ban command with flexible durations.

---

## Step 1 — Turn these files into a .jar (free, no coding)

The easiest way is to let GitHub build it for you:

1. Make a free account at github.com if you don't have one.
2. Click **New repository** → give it a name (e.g. `summersmp-core`) → **Create**.
3. On the new repo page, click **Add file ▸ Upload files**, then drag in **everything from this folder** (keep the folder structure — drag the whole `SummerSMPCore` contents in). Click **Commit changes**.
4. Go to the **Actions** tab. A job called **Build Plugin** runs automatically. Wait for the green check (about a minute).
5. Click into that run → scroll to **Artifacts** → download **SummerSMPCore-jar**. Unzip it — inside is `SummerSMPCore-1.0.0.jar`.

(Prefer to build locally instead? Install **JDK 25** and **Maven**, then run `mvn package` in this folder — the jar lands in `target/`.)

---

## Step 2 — Add it to your Exaroton server

1. Open your server on exaroton.com.
2. Go to **Files** (the file manager) → open the **`plugins`** folder.
3. **Upload** `SummerSMPCore-1.0.0.jar` into it.
4. **Restart** the server (Stop, then Start).
5. Check the **Console** — you should see `SummerSMPCore | Enabled. Mace limit: 3 ...`.

You'll also need **LuckPerms** in the same `plugins` folder if you want to hand the ban command to specific staff (see below).

---

## Step 3 — Give your anti-cheat staff the /ban command

By default only server operators can use `/ban`. To give it to a staff rank with LuckPerms, run in console or in-game:

```
/lp group staff permission set summersmp.ban true
/lp group staff permission set summersmp.unban true
```

(Replace `staff` with whatever your anti-cheat group is called.)

---

## Command reference

| Command | What it does |
|---|---|
| `/ban <player> <duration> [reason]` | Temp-ban. Duration can be `12 days`, `7d`, `30 minutes`, `2w`, `6 months`, or `perm`. |
| `/unban <player>` | Removes a ban. |
| `/maces view` | Shows how many Heavy Cores are claimed (e.g. 2/3). |
| `/maces set <n>` | Manually set the claimed count (e.g. after wiping the map: `/maces set 0`). |
| `/maces setlimit <n>` | Change the maximum number of Maces. |
| `/maces reload` | Reload `config.yml`. |

## config.yml

```yml
mace-limit: 3                      # max Maces (heavy cores) that can ever be found
announce-heavy-core-claims: true   # broadcast in chat when one is claimed
default-ban-reason: "Banned by staff"
```

## Notes / good to know

- The Mace cap counts Heavy Cores as players **pick them up** (that's how ominous-vault loot reaches a player). The count is saved in `data.yml` and survives restarts. If you reset your world, run `/maces set 0`.
- If you ever update the server to **26.2**, rebuild the jar after bumping `api-version` in `plugin.yml` to `26.2`.
- The ban command uses the server's own ban list, so bans are enforced automatically until they expire.
