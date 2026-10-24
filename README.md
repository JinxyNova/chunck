# ChunkRandomBlock

Plugin **Paper 26.2** (Java 25) : chaque chunk dans lequel un joueur entre pour la première fois
se transforme en **un seul bloc aléatoire** (n'importe quel bloc solide du jeu).

## Fonctionnement
- Au moment où tu entres dans un chunk, un bloc est tiré au hasard et tous les blocs solides du chunk deviennent ce bloc.
- Les blocs non solides (air, eau, plantes) ne changent pas, donc tu ne restes pas coincé.
- La conversion est étalée sur plusieurs ticks, de haut en bas, pour rester fluide.
- Un chunk converti ne change plus jamais (marqueur stocké dans le chunk).
- Si le serveur s'arrête en pleine conversion, elle reprend avec le même bloc.
- Sable, gravier, etc. sont possibles mais ne tombent pas tant qu'on ne casse / pose pas un bloc à côté.
- Blocs jamais utilisés : portes, lits, coffres, fours, panneaux, bannières, spawners, command blocks, bedrock, pistons…

## Installation
1. Récupère le `.jar` dans l'onglet **Actions** (artifact `ChunkRandomBlock`) ou dans **Releases**.
2. Mets-le dans le dossier `plugins/` d'un serveur Paper 26.2 (Java 25).
3. Démarre le serveur.

## Configuration (`plugins/ChunkRandomBlock/config.yml`)
| Clé | Description |
|---|---|
| `worlds` | Mondes concernés (liste vide = tous) |
| `checks-per-tick` | Nombre de blocs analysés par tick |
| `changes-per-tick` | Nombre de blocs modifiés par tick |
| `freeze-ticks` | Durée pendant laquelle le sable/gravier ne peut pas tomber après la conversion |
| `announce` | Affiche le bloc choisi dans l'action bar |
| `blacklist` | Blocs à ne jamais tirer (ex. `TNT`) |

Baisse `checks-per-tick` / `changes-per-tick` si tu as des lags, monte-les pour aller plus vite.

## Compiler
```bash
gradle shadowJar
```
Le jar se trouve dans `build/libs/` (Java 25 requis).

## Publier une release
```bash
git tag v1.0.0
git push origin v1.0.0
```
GitHub Actions compile et attache le jar à la release automatiquement.
