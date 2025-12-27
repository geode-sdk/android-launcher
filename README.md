# Geode Android Launcher

Launches a vanilla copy of Geometry Dash with the Geode loader added.

## Shortcut Links

The launcher supports a handful of links as an escape hatch when the main functionality is impaired in some way (such as needing to prevent an automatic launch due to a crash).  
You can open these links by copying them into the URL bar in some browsers (like Chrome), or through ADB.

| Link                                        | Action                                                                   |
|---------------------------------------------|--------------------------------------------------------------------------|
| geode-launcher://main                       | Opens to the main activity, forces the user to explicitly confirm launch |
| geode-launcher://main/launch                | Opens to the game after the launcher is finished checking for updates    | 
| geode-launcher://main/launch?safe-mode=true | Opens to the game like before, but with safe mode enabled                |
| geode-launcher://settings                   | Opens the main settings                                                  |
| geode-launcher://developer-settings         | Opens the developer settings                                             |
| geode-launcher://logs                       | Opens the application logs                                               |
| geode-launcher://crashes                    | Opens the crash dumps viewer                                             |
