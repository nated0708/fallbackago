# SimpleFallback

Fallback plugin for **Waterfall 1.21** (also works on BungeeCord). When a backend server dies,
players on it are moved to a configured fallback server instead of being kicked to the login screen.

## How it stays reliable

Three layers, because a dying server can drop players in several different ways:

1. **`ServerKickEvent`** – the normal case (server shutting down, restart, crash kick). The kick is
   cancelled and the player is rerouted with `setCancelServer()`.
2. **`ServerConnectEvent`** – catches Waterfall's own `SERVER_DOWN_REDIRECT` / `LOBBY_FALLBACK`
   handling and any connect aimed at a server we know is offline (including first join when the
   default lobby is down), then overrides the target.
3. **Health checker** – pings every backend every few seconds. This does two things: it guarantees
   we never route a player into a fallback that is *also* dead (it walks down the list until one
   answers), and it *evacuates* players still sitting on a server that froze rather than closed
   cleanly — those players would otherwise just hang until they time out.

A server is also flagged dead instantly if its kick message matches `server-down-reasons`.

## Build

```bash
mvn clean package
```
Output: `target/SimpleFallback-1.0.0.jar` → drop in `plugins/`, restart the proxy.

Requires JDK 17+. The Waterfall API is pulled from `repo.papermc.io`.

## Config (`plugins/SimpleFallback/config.yml`)

```yaml
fallbacks:
  survival:
    - lobby      # tried first
    - lobby2     # tried if lobby is also down
global-fallback:
  - lobby
  - lobby2
```

Server names must match the ones in your Waterfall `config.yml`. `fallbacks` is per-source-server;
`global-fallback` is used when the source server has no entry or all of its targets are down.
The dead server is never used as its own fallback.

Key settings:

| Setting | Default | What it does |
|---|---|---|
| `health-check.enabled` | `true` | Ping backends to track up/down state |
| `health-check.interval` | `5` | Seconds between pings |
| `health-check.failures-before-dead` | `2` | Ping failures before a server counts as dead |
| `evacuate-on-death` | `true` | Pull players off a frozen/crashed server automatically |
| `require-server-offline` | `false` | Set `true` so only *real* outages trigger fallback (normal `/kick` passes through) |
| `per-server-permission` | `false` | Require `simplefallback.server.<name>` to be sent to a target |
| `message-delay` | `500` | ms before showing message/title, so it lands after the switch |

`ignored-kick-reasons` (bans, whitelist, etc.) are never cancelled — those players still get kicked.

## Lang (`plugins/SimpleFallback/lang.yml`)

Chat message, disconnect message and an optional title/subtitle. `&` colour codes.
Placeholders: `%prefix%`, `%player%`, `%from%`, `%to%`, `%reason%`. Set any message to `""` to disable it.

## Commands

- `/sfallback reload` – reloads config.yml + lang.yml and restarts the health checker
- `/sfallback status` – lists every backend with online/offline state

Permission: `simplefallback.admin`

## Recommended proxy setting

In Waterfall's own `config.yml`, keep at least one entry in your listener's `priorities:` list.
It's a last-resort net for the rare path where the proxy drops a connection before any event fires:

```yaml
listeners:
  - priorities:
      - lobby
      - lobby2
```
