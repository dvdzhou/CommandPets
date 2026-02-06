# CommandPets 🐾

**CommandPets** is a Minecraft server plugin (Spigot/Paper 1.21+) that turns your vanilla animals into smart, reliable companions.
It solves the daily frustrations of pet ownership—like losing dogs in caves or constant barking—by giving you advanced control over their movement, safety, and behavior.

![Version](https://img.shields.io/badge/version-1.0-blue)
![Minecraft](https://img.shields.io/badge/minecraft-1.21+-green)
![License](https://img.shields.io/badge/license-MIT-red)

## ✨ Key Features

### 🛡️ Smart Protection
* **Bodyguard AI:** Your dogs automatically defend you from monsters and hostile players, but they know when to stop.
* **Threat Detection:** Dogs recognize if a player approaches you holding a weapon (Sword, Axe, Trident, Mace) and react accordingly.
* **Friend System:** Add players to a whitelist so your pets never attack your friends.

### 🏠 Management & QoL
* **Pack Management:** Easily handle large groups of pets. Movement commands are optimized to keep your pack organized and prevent overcrowding.
* **Smart Silence:** Toggle "Silent Mode" to instantly mute your pets when they are sitting, so you can enjoy their company without the noise.
* **Baby Safety:** Movement commands default to **Adults Only**. Your puppies and kittens stay safe at home unless you explicitly call them.
* **Persistence:** Friends list, home locations, and settings are saved automatically.

## 📜 Commands

The main command is `/pets` (aliases: `/cpets`, `/mypets`).

### 📡 Movement
*You can filter by type (`dogs`, `cats`) and age (`adults`, `babies`, `all`).*

* `/pets call [dogs|cats] [adults|babies|all]`  
  *Teleports pets to you and makes them stand.* **Default:** Adults only (keeps babies safe).
* `/pets gohome [dogs|cats] [adults|babies|all]`  
  *Sends pets to the saved home location and makes them sit.* **Default:** Adults only. Use `/pets gohome all` to move everyone.
* `/pets sit [dogs|cats] [all]`  
  *Makes nearby pets sit immediately.* **Default:** Everyone.
* `/pets stand [dogs|cats] [all]`  
  *Makes nearby pets stand up.* **Default:** Everyone.
* `/pets sethome`  
  *Sets your current location as the permanent "Home" for your pets.*

### ⚔️ Combat & Settings
* `/pets attack [on|off]`  
  *Toggles the bodyguard AI. Useful if you want a peaceful playthrough.*
* `/pets silence [on|off]`  
  *Toggles "Silent Mode". If ON, sitting pets become instantly silent.*
* `/pets count`  
  *Shows a census of your currently loaded pets (Dogs vs Cats).*

### 🤝 Friends System
* `/pets friends add <player>`  
  *Adds a player to your safe whitelist.*
* `/pets friends remove <player>`  
  *Removes a player from your whitelist.*
* `/pets friends list`  
  *Displays your current trusted friends.*

## ⚙️ Installation

1.  Download the latest `CommandPets-1.0.jar` from Releases.
2.  Place it into your server's `plugins` folder.
3.  Restart the server.
4.  Enjoy your new companions!

## 🛠️ Building from Source

To modify or compile the code yourself:

1.  Clone this repository.
2.  Ensure you have **Java 21** (JDK 21) installed.
3.  Build with Maven:
    ```bash
    mvn package
    ```
4.  The compiled jar will be in the `target/` directory.

## 📝 Technical Notes

* **API Version:** Spigot/Paper 1.21.11 API.
* **Safety Logic:** The teleport system scans for solid ground and avoids cliffs/walls. If a pet cannot find a safe spot, it will not teleport.
* **Data:** Saved in `plugins/CommandPets/data.yml`.

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

## 🌟 Credits

* **Daluxea** - *Concept, Design & Project Management*
* **Gemini (AI)** - *Code Generation & Implementation*
