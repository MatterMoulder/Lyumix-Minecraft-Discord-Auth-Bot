# 🔐 Lyumix Discord Auth

A secure Minecraft server authentication mod that uses Discord for two-factor verification.

## ✨ Features

- 🔒 Two-factor authentication through Discord
- 🕐 Configurable auto-login system
- 🌍 Multiple database support (MongoDB, PostgreSQL, SQLite)
- ⚡ Lightweight and performant
- 🎮 Seamless player experience
- 🛡️ Advanced security features

## 🛠️ Setup

### Prerequisites
- Minecraft Server 1.21 - 1.21.1
- Fabric Loader 0.16.3+
- Java 21+
- Discord Bot Token

### Installation
1. Download the latest release from [Modrinth](https://modrinth.com/mod/lyumix-discord-auth)
2. Place the .jar file in your server's `mods` folder
3. Start the server once to generate configuration files
4. Configure the mod in `config/lyumix-discord-auth/config.hocon`

## ⚙️ Configuration

The mod creates two configuration files on first start:
- `config/lyumix-discord-auth/config.hocon` - Main configuration
- `config/lyumix-discord-auth/messages.hocon` - Customizable messages

### Example Configuration

<details>
<summary>Click to expand example configuration</summary>

```hocon
database {
# Database type (mongodb, postgresql, sqlite)
type = "sqlite"
# Connection string (required for MongoDB and PostgreSQL)

connectionString = ""
# Database credentials (PostgreSQL only)
username = ""
password = ""
}
discord {
# Your Discord bot token
botToken = "your-token-here"
# Discord server ID (leave empty to allow all servers)
discordServerId = ""
# Allow users to unlink their accounts
allowUserUnlink = true
}
login {
# Enable blindness effect while not authenticated
blindnessWhileLogin = true
# Auto-login time in hours (0 to disable)
autoLoginTime = 24
}
loginTimer {
# Enable login timeout
enabled = true
# Login timeout in seconds
loginTime = 60
# Timer display settings
title = "Time remaining: "
firstColor = "GREEN"
secondColor = "YELLOW"
thirdColor = "RED"
secondTime = 30
thirdTime = 15
}
```
</details>

### Key Configuration Options
- Database settings (SQLite, MongoDB, PostgreSQL)
- Discord bot configuration
- Authentication timeouts
- Auto-login duration
- Visual settings for login timer

## 📝 Commands

### Server Commands (Operators Only)
- `/lda reload` - Reload configuration
- `/lda force unlink player <name>` - Force unlink player's Discord account
- `/lda force unlink discord <id>` - Force unlink by Discord ID
- `/lda force link <player> <discordId>` - Force link player to Discord account

### Discord Commands
- `/register <username>` - Link your Minecraft account to Discord
- `/status` - View linked accounts
- `/unlink` - Unlink Minecraft account (if enabled)

## 🔧 Database Support

### Supported Databases
- SQLite (default, no setup required)
- MongoDB
- PostgreSQL

Each database type offers different benefits:
- SQLite: Simple, self-contained, perfect for small servers
- MongoDB: Scalable, great for large servers
- PostgreSQL: Robust, ideal for complex setups

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ❓ Support

Need help? Here's how to get support:
1. Check the [Wiki](https://github.com/MatterMoulder/Lyumix-Minecraft-Discord-Auth-Bot/wiki)
2. Create an [Issue](https://github.com/MatterMoulder/Lyumix-Minecraft-Discord-Auth-Bot/issues)
3. Join our [Discord server](https://discord.gg/rUGNCSzJSG)

## 🔐 Security

The mod implements several security features:
- Two-factor authentication through Discord
- Configurable login timeouts
- IP-based auto-login system
- Secure database storage

## ❓ FAQ

**Q: Can I use this mod on a client?**
A: No, this is a server-side only mod.

**Q: Does this work with other authentication mods?**
A: It's recommended to use only one authentication mod at a time to avoid conflicts.

**Q: Can I customize the messages?**
A: Yes, all messages can be customized in the `messages.hocon` file.

**Q: How secure is the Discord authentication?**
A: The mod uses Discord's secure OAuth2 system and implements additional security measures like IP-based auto-login and configurable timeouts.

## 🔄 Compatibility

### Compatible Mods
- Fabric API (required)
- Most other Fabric mods that don't modify player authentication

### Known Incompatibilities
- Other authentication mods
- Mods that significantly modify player login behavior

## 🚀 Roadmap

### Planned Features
- [ ] Additional database support
- [ ] Enhanced auto-login features
- [ ] Multi-factor authentication options
- [ ] Integration with other platforms

### In Progress
- [ ] Performance optimizations
- [ ] Additional security features
- [ ] Extended API for developers

## 👥 Contributing

We welcome contributions! Here's how you can help:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Development Setup
1. Clone the repository
2. Import as a Gradle project
3. Run `./gradlew build` to build the mod
4. Run `./gradlew runServer` to test with a local server

## 📊 Build Status

![Build Status](https://github.com/MatterMoulder/Lyumix-Minecraft-Discord-Auth-Bot/actions/workflows/build.yml/badge.svg)
[![Release](https://img.shields.io/github/v/release/MatterMoulder/Lyumix-Minecraft-Discord-Auth-Bot)](https://github.com/MatterMoulder/Lyumix-Minecraft-Discord-Auth-Bot/releases)
