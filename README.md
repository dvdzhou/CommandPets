# CommandPets ⚔️

**CommandPets** is a Minecraft server plugin (Spigot/Paper 1.21+) that transforms your tamed animals into tactical companions. 
It allows you to manage dogs and cats with squad commands, teleport them, set a "home" base, and toggle an advanced bodyguard mode.

![Version](https://img.shields.io/badge/version-1.0-blue)
![Minecraft](https://img.shields.io/badge/minecraft-1.21+-green)
![License](https://img.shields.io/badge/license-MIT-red)

## ✨ Features

* **🛡️ Smart Bodyguard:** Your dogs automatically attack monsters and hostile players that get too close to you.
* **⚔️ Threat Detection:** Dogs recognize if a player approaches you holding a weapon (Sword, Axe, Trident, Mace) and react accordingly.
* **🤝 Friend System (Whitelist):** Add your friends to a safe list so your dogs never attack them.
* **📡 Tactical Commands:** Call to you (`call`), make them sit (`sit`), or send home (`gohome`) all your pets with a single command.
* **🐈 Multi-Species Support:** Specific commands for dogs only, cats only, or both.
* **💾 Persistence:** Friends list, home locations, and settings are saved automatically.

## 📜 Commands

The main command is `/pets` (aliases: `/cpets`, `/mypets`).

### Movement & Management
* `/pets call [dogs|cats]`  
    *Teleports pets to your location and makes them stand up (ready for action).*
* `/pets gohome [dogs|cats]`  
    *Sends pets to the saved home location and makes them sit.*
* `/pets sit [dogs|cats]`  
    *Makes all nearby pets sit immediately.*
* `/pets stand [dogs|cats]`  
    *Makes all nearby pets stand up.*
* `/pets sethome`  
    *Sets your current location as the "Home" point for your pets.*

### Combat & Friends
* `/pets attack <on|off>`  
    *Toggles the automatic attack mode for your dogs. Useful if you want to be peaceful.*
* `/pets friends add <player>`  
    *Adds a player to your whitelist (your pets will ignore them).*
* `/pets friends remove <player>`  
    *Removes a player from your whitelist.*
* `/pets friends list`  
    *Displays your current list of trusted friends.*

## ⚙️ Installation

1.  Download the `CommandPets-1.0.jar` file.
2.  Place it into your server's `plugins` folder.
3.  Restart the server.
4.  Enjoy your tactical army!

## 🛠️ Building from Source

If you want to modify the source code:

1.  Clone this repository.
2.  Ensure you have **Java 21** (JDK 21) installed.
3.  Build with Maven:
    ```bash
    mvn package
    ```
4.  You will find the compiled jar in the `target/` directory.

## 📝 Technical Notes

* **API Version:** Built against Spigot/Paper 1.21.11 API.
* **Protection Radius:** The auto-protection radius is set to **10 blocks**.
* **Data:** The plugin saves data in `plugins/CommandPets/data.yml`.

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

## 🤝 Credits

* **Daluxea** - *Concept, Design & Project Management*
* **Gemini (AI)** - *Code Generation & Implementation*