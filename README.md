# Core2FA — Two-Factor Authentication via Telegram

**Core2FA** is a Minecraft plugin (Spigot, Paper, Folia) for **1.21+** that adds two-factor authentication through Telegram.  
It protects player accounts from hijacking by requiring login confirmation through a linked Telegram account.  
With Telegram integration, players can manage their account security quickly and easily from their phone.

---

## ✨ Features

- **Account Linking**: Link your Minecraft account to your Telegram account for extra security.
- **Login Confirmation**: Players receive Telegram notifications to approve or deny logins.
- **Remote Security Controls**: Through an interactive Telegram menu, players can:
  - Approve or deny logins
  - Block their account if suspicious activity is detected (if enabled)
  - Unlink their account (if allowed by server admin)
  - Access all settings using the **`/menu` command in Telegram**
- **Configurable Options**: Adjustable language, custom server name in notifications, and fine-grained permission controls.
- **Folia Support**: Optimized for Folia-based servers.

---
## 📜 Commands

| Command            | Aliases         | Description                                                    |
| ------------------ | --------------- | -------------------------------------------------------------- |
| `/2fa`             | `/tfa`, `/auth` | Main command to manage 2FA                                     |
| `/2fa reload`      | —               | Reloads the plugin configuration                               |
| `/menu` (Telegram) | —               | Opens the interactive Telegram menu to manage account remotely |

---

## 🔑 Permissions

| Permission     | Description                                 |
| -------------- | ------------------------------------------- |
| `twofa.reload` | Reloads the plugin configuration (OPs only) |

---

## 🖥 Compatibility

* **Minecraft Version:** 1.21+
* **Platform:** Spigot, Paper, Folia
* **Java:** 17+

---

## 📌 Example Usage

1. A player joins the server.
2. They receive a Telegram message: *"Confirm login to MyServer?"*
3. They press "Confirm" or "Deny".
4. The login is either allowed or blocked.
5. Players can use **`/menu` in Telegram** to manage security settings or unlink their account.
6. Server admins can use `/2fa reload` to apply configuration changes without restarting.

---
## 📄 License

MIT License – free to use, share, or modify.

