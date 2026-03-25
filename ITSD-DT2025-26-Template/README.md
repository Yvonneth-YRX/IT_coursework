# ITSD Card Game 25-26

## Introduction

This project is our Part 2 implementation for the IT+ Masters Team Project 2025-26. The aim of this stage was to build the backend logic for a simplified Duelyst-style tactical card game using the Java template provided in the coursework.

The game is played as a single match between a human player and an AI opponent. The user interacts with the game through the browser, while the main rules and state changes are handled on the Java backend. Communication between the frontend and backend is done through WebSocket.

At this point, the project supports a full playable match with turn flow, card play, unit movement, combat, spell effects, and AI turns.

## Aim of the Project

The main goal of the project was not to build a complete commercial card game, but to implement the core systems required by the assignment:

  core game loop logic
  game state tracking
  card logic for the required cards
  a working AI opponent

The implementation was built on top of the provided template rather than from scratch, so part of the work involved extending the template code into a complete playable system.

## What the Game Currently Supports

The game currently includes:

  a 9x5 game board
  one human player and one AI player
  one avatar for each side
  decks and hands for both sides
  turn-based progression
  mana refresh each turn
  unit summoning
  spell casting
  unit movement
  combat between units
  health and attack updates
  death handling
  win and loss conditions
  summon effects before a unit appears on the board 
  AI turns with card play, movement, and attack decisions

In practical terms, the game can be started, played through, and finished.

## Gameplay Summary

At the start of the match, the board is created, both avatars are placed, and the two decks are initialised and shuffled. Both sides draw their opening hand. The human player starts first.

During a turn, the player can:

  select a card from hand
  summon a unit onto a legal tile
  cast a spell on a legal target
  select a friendly unit
  move the selected unit 
  attack an enemy unit
  cancel a unit selection
  end the turn

After the human player ends the turn, the AI automatically performs its own actions. The match ends when either avatar reaches zero health.

## Short Game Guide

The aim of the game is to defeat the enemy avatar before your own avatar is defeated.

Some basic rules of play are:

  the game is played on a 9x5 board
  each player has an avatar, a deck, a hand, and mana
  at the start of a turn, the current player receives mana 
  cards in hand can be either creatures or spells
  creature cards summon units onto legal board tiles
  spell cards affect legal targets depending on the spell
  units can move and attack during the turn if they are allowed to act
  if an enemy unit with `Provoke` is blocking nearby, attacks must be directed to that unit
  some cards use mechanics such as `Flying`, `Rush`, `Stun`, `Opening Gambit`, and `Deathwatch`
  after the human player ends the turn, the AI takes its turn automatically
  the game ends when either avatar reaches 0 health

This is only a short guide for using the project. The full detailed behaviour of each card depends on the implemented card logic and the assignment rule set.

## Cards Implemented

The project includes all 20 cards required by the assignment configuration files.

Human-side cards:

  Bad Omen
  Horn of the Forsaken
  Gloom Chaser
  Shadow Watcher
  Wraithling Swarm
  Nightsorrow Assassin
  Rock Pulveriser
  Dark Terminus
  Bloodmoon Priestess
  Shadowdancer

AI-side cards:

  Skyrock Golem
  Swamp Entangler
  Silverguard Knight
  Saberspine Tiger
  Beamshock
  Young Flamewing
  Silverguard Squire
  Ironcliff Guardian
  Sundrop Elixir
  Truestrike

## Mechanics Implemented

The following mechanics are implemented in the current version:

  Provoke
  Flying
  Rush
  Stun
  Airdrop
  Opening Gambit
  Deathwatch
  direct spell damage
  healing
  destroy-and-replace effects
  horn durability and token summon behaviour

Some card-specific examples are:

  `Gloom Chaser` summons a Wraithling behind itself
  `Nightsorrow Assassin` destroys a damaged adjacent enemy
  `Bloodmoon Priestess` summons a Wraithling when a unit dies
  `Shadowdancer` damages the enemy avatar and heals itself on deathwatch
  `Ironcliff Guardian` can be summoned using Airdrop and has Provoke
  `Sundrop Elixir` heals a unit without allowing avatar targeting
  `Truestrike` only targets enemy units

## Important Fixes Made During Development

During development and testing, several issues were found and corrected. The more important fixes included:

  correcting `Sundrop Elixir` so that its healing matches the card text
  stopping `Sundrop Elixir` from targeting avatars
  stopping `Truestrike` from targeting avatars
  implementing `Airdrop` correctly for `Ironcliff Guardian`
  fixing `Provoke` activation for `Ironcliff Guardian`
  giving each card instance a unique id instead of reusing the same values
  fixing incorrect hand redraw behaviour where AI hand updates affected the human hand display
  fixing unit deselection so that a selected unit can be clicked again to cancel selection
  adding a summon animation so units do not appear instantly on the board

## Project Structure

```text
ITSD-DT2025-26-Template/
├── app/
│   ├── actors/         # WebSocket actor logic
│   ├── commands/       # Commands sent from backend to frontend
│   ├── controllers/    # HTTP and WebSocket entry points
│   ├── events/         # Frontend event handlers
│   ├── structures/     # Main game logic, AI, turn system, state tracking
│   ├── utils/          # Object loaders and helper classes
│   └── views/          # Play page template
├── conf/
│   ├── application.conf
│   ├── routes
│   └── gameconfs/      # JSON files for cards, units, avatars, effects, board
├── app/assets/         # Frontend scripts and visual resources
├── test/               # JUnit tests
├── build.sbt
└── scripts/
```

## Main Files

  [`GameScreenController.java`](app/controllers/GameScreenController.java)  
  Serves the game page and creates the WebSocket connection.

  [`GameActor.java`](app/actors/GameActor.java)  
  Manages a game session, initialises decks, and routes incoming messages to the correct event processor.

  [`GameState.java`](app/structures/GameState.java)  
  Contains most of the gameplay logic, including summoning, targeting, movement, attacks, death handling, and many card effects.

  [`TurnSystem.java`](app/structures/TurnSystem.java)  
  Controls turn switching, mana refresh, and card draw.

  [`AIController.java`](app/structures/AIController.java)  
  Handles AI decision-making for attacks, movement, and card play.

  [`cardgame.js`](app/assets/js/cardgame.js)  
  Handles rendering, animations, input, hand display, and frontend-side game presentation.

## How the System Works

When the user opens the game page, the browser connects to the backend through WebSocket. A `GameActor` is created for that game session. The frontend sends JSON event messages such as card clicks, tile clicks, unit movement notifications, and end-turn clicks.

The backend processes these events, updates the current `GameState`, and sends command messages back to the frontend. These commands are used to draw units, update cards, move units, show notifications, and play effects.

This means the frontend mostly handles presentation, while the backend remains responsible for the rules.

## Running the Project

Open a terminal in the project folder and run:

```bash
cd ITSD-DT2025-26-Template
./sbt run
```

Then open the game in the browser at:

```text
http://localhost:9000/game
```

The WebSocket route used by the game is:

```text
/gamews
```

## Testing

The project currently includes a sample JUnit test:

  [`InitalizationTest.java`](test/InitalizationTest.java)

Tests can be run with:

```bash
cd ITSD-DT2025-26-Template
./sbt test
```

At the moment, automated testing is still limited. Most recent validation has been done through manual gameplay testing and direct checking of card behaviour against the assignment card configuration files.

## Current Limitations

There are still a few limitations in the current project:

  automated test coverage is not yet comprehensive
  some names inherited from the original template are still misspelled, for example `Initalize`
  the project only supports a single playable match and does not include wider game systems such as menus, matchmaking, or deck building

These do not stop the match from being played, but they are areas that could still be improved.

## Final Summary

Overall, this project now provides a complete playable tactical card game match within the scope of the coursework. The core game loop works, the game state is tracked on the backend, all required cards are implemented, and the AI can take legal turns and provide opposition to the player.

The main area still needing improvement is automated testing, but the core functionality required for Part 2 has been implemented and integrated into the provided framework.
