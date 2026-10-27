# ItemRush

Mini-jeu survival rapide pour Minecraft : une roulette d’items tourne à l’écran, un item cible est choisi, et le **premier joueur qui ramasse exactement cet item** gagne une récompense.

Compatible **Fabric** et **NeoForge**. Tout se déroule dans le monde normal (pas de dimension custom).

## Versions supportées

| Minecraft | Fabric | NeoForge | JARs |
|-----------|--------|----------|------|
| **26.3** (défaut) | ✅ | ✅ | `dist/26.3/` |
| **26.2** | ✅ | ✅ | `dist/26.2/` |
| **26.1.2** | ✅ | ✅ | `dist/26.1.2/` |
| **1.21.11** | ✅ | ✅ | `dist/1.21.11/` |

Build multi-version via `build-version.ps1` (bascule Loom / Loom-Remap + Java 21/25 automatiquement).

## Installation (Modrinth / manuel)

1. Installez Fabric Loader **ou** NeoForge pour votre version Minecraft.
2. Fabric uniquement : ajoutez aussi [Fabric API](https://modrinth.com/mod/fabric-api).
3. Placez le JAR correspondant (`itemrush-fabric-…` ou `itemrush-neoforge-…`) dans le dossier `mods/`.
4. Les deux loaders n’ont **pas** besoin d’Architectury ni d’autre dépendance obligatoire.

JARs prêts à publier : dossier [`dist/`](dist/).

## Commandes

| Commande | Permission | Description |
|----------|------------|-------------|
| `/itemrush start` | niveau 2 (configurable) | Démarre une manche avec la durée par défaut |
| `/itemrush start <secondes>` | idem | Démarre avec une durée custom (5–3600) |
| `/itemrush stop` | idem | Arrête la manche en cours |
| `/itemrush status` | tout le monde | Affiche l’état (idle / roulette / cible + temps) |
| `/itemrush reload` | niveau 2 | Recharge `config/itemrush.json` |

## Déroulement d’une manche

1. Un admin lance `/itemrush start`.
2. Une **roulette** d’items s’affiche côté client et s’arrête sur l’item cible (choisi côté serveur).
3. Un **timer** apparaît en haut de l’écran.
4. Les joueurs doivent **ramasser** l’item exact (ID / type) dans le monde.
5. Le **premier** qui le ramasse gagne immédiatement une récompense pondérée.
6. Si le timer arrive à 0 → message « Time’s up ».

Détection **100 % serveur** via le ramassage réel d’un `ItemEntity` (anti-triche basique : pas de `/give`, pas de mutation d’inventaire inventée).

## Configuration

Fichier généré au premier lancement : `config/itemrush.json`

- Durée par défaut, durée / vitesse de la roulette
- Niveau de permission des commandes (0–4)
- Pool d’items cibles **ou** tous les items (`use_all_items`)
- Récompenses pondérées : `item`, `xp`, `command`
- Messages custom, sons, particules

Exemple de récompense :

```json
{
  "type": "item",
  "id": "minecraft:diamond",
  "count": 3,
  "weight": 10
}
```

## Build

Prérequis : **JDK 25**, Git.

```bash
# Build version courante (gradle.properties → 26.3 par défaut)
./gradlew build

# Build une version précise (Windows)
./build-version.ps1 -MinecraftVersion 26.3
./build-version.ps1 -MinecraftVersion 26.2
./build-version.ps1 -MinecraftVersion 26.1.2
./build-version.ps1 -MinecraftVersion 1.21.11
```

Sorties :

- `fabric/build/libs/itemrush-fabric-<mc>-<ver>.jar`
- `neoforge/build/libs/itemrush-neoforge-<mc>-<ver>.jar`

## Architecture

Projet **MultiLoader** (common + fabric + neoforge) sans Architectury API.

- **common** : logique de manche, config, commandes, payloads réseau, HUD / roulette, mixin de pickup
- **fabric** / **neoforge** : entrypoints, envoi de paquets, enregistrement HUD / tick

## Licence

MIT — voir [LICENSE](LICENSE).
