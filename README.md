# NightMarketAPI

NightMarketAPI is the developer-facing API for integrating custom currencies and market-aware features into the NightMarket plugin.

It allows other plugins to:

- register custom economy providers
- check whether the market is currently open
- read the remaining time in the current cycle
- listen for market open and market close events

## What You Can Do

### 1. Register a custom economy provider
You can plug your own currency system into NightMarket by implementing `EconomyProvider` and registering it with `APIManager`.

### 2. Read market state
You can check:

- whether the market is open
- the remaining time in seconds
- the formatted remaining time string

### 3. Listen to market lifecycle events
You can react to:

- `MarketOpenEvent`
- `MarketCloseEvent`

These events also tell you whether the action was forced by an admin command.

## Dependency

NightMarketAPI can be consumed through JitPack.

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.AllyncID</groupId>
        <artifactId>NightMarketAPI</artifactId>
        <version>&lt;tag&gt;</version>
    </dependency>
</dependencies>
```

### Gradle

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.AllyncID:NightMarketAPI:&lt;tag&gt;'
}
```

Replace `&lt;tag&gt;` with the version tag you want to use.

## Server Plugin Dependency

In your plugin's `plugin.yml`, make sure NightMarket is loaded before your integration if you register your provider during `onEnable()`.

```yml
softdepend:
  - NightMarket
```

Use `depend` instead of `softdepend` if your plugin cannot work without NightMarket.

## Main API Classes

### `APIManager`
Provides static helper methods for integration:

- `registerEconomyProvider(String name, EconomyProvider provider)`
- `getProvider(String name)`
- `isCustomProvider(String name)`
- `isMarketOpen()`
- `getTimeRemainingSeconds()`
- `getFormattedTimeRemaining()`

### `EconomyProvider`
Interface to implement for custom currencies:

```java
public interface EconomyProvider {
    String getName();
    double getBalance(OfflinePlayer player);
    boolean has(OfflinePlayer player, double amount);
    boolean withdraw(OfflinePlayer player, double amount);
    String format(double amount);
}
```

## Registering a Custom Provider

Example provider:

```java
package your.plugin.economy;

import me.allync.nightmarket.economy.EconomyProvider;
import org.bukkit.OfflinePlayer;

public class GemsEconomyProvider implements EconomyProvider {

    @Override
    public String getName() {
        return "GEMS";
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return 1000;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return true;
    }

    @Override
    public String format(double amount) {
        return amount + " Gems";
    }
}
```

Register it in `onEnable()`:

```java
import me.allync.nightmarket.api.APIManager;

@Override
public void onEnable() {
    APIManager.registerEconomyProvider("GEMS", new GemsEconomyProvider());
}
```

Then set the provider name in NightMarket's `config.yml`:

```yml
economy:
  enabled: true
  provider: "GEMS"
```

## Reserved Provider Names

Do not register a custom provider using built-in names such as:

```text
VAULT
PLAYER_POINTS
PLAYERPOINTS
TOKEN_MANAGER
TOKENMANAGER
COINSENGINE
```

## Reading Market State

```java
boolean open = APIManager.isMarketOpen();
long seconds = APIManager.getTimeRemainingSeconds();
String formatted = APIManager.getFormattedTimeRemaining();
```

## Listening to Events

### Example Listener

```java
import me.allync.nightmarket.api.events.MarketCloseEvent;
import me.allync.nightmarket.api.events.MarketOpenEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MarketListener implements Listener {

    @EventHandler
    public void onMarketOpen(MarketOpenEvent event) {
        if (event.isForced()) {
            // Opened by admin command
        } else {
            // Opened by scheduled cycle
        }
    }

    @EventHandler
    public void onMarketClose(MarketCloseEvent event) {
        if (event.isForced()) {
            // Closed by admin command
        } else {
            // Closed by scheduled cycle
        }
    }
}
```

## Best Practices

- Register your provider during plugin startup
- Keep the provider name uppercase and consistent
- Use unique names for custom currencies
- Add NightMarket as a dependency in `plugin.yml` when needed
- Fail gracefully if NightMarket is not present

## Example Use Cases

- Gems, tokens, souls, crystals, or other custom currencies
- Boss reward currencies
- Seasonal event currencies
- External economy bridges
- Plugins that react when the market opens or closes

## License

Add your preferred license here.
