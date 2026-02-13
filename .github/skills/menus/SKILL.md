---
name: menus
description: 'ChatControl GUI menus: color picker menu, spy toggle menu, channel list menu, Foundation Menu system, menu configuration, click actions, animated menus, paged menus. Use when diagnosing menu display, GUI interaction, or menu configuration issues.'
---

# GUI Menus

## Overview

ChatControl uses Foundation's menu framework to provide interactive inventory GUIs: color picker, spy toggle, channel browser, and more. Menus are Java-based using Foundation's `Menu`, `MenuPagged`, and `MenuStandard` classes.

## Architecture

### Key Classes
- `ColorMenu` (menu/ColorMenu.java) — color/decoration picker using wool/carpet items
- `SpyMenu` (menu/SpyMenu.java) — toggle spy on different message types
- `ChannelMenu` — channel listing/joining GUI
- `Menu` (Foundation) — base menu class with inventory management
- `MenuPagged<T>` (Foundation) — paginated menu for lists
- `MenuStandard` (Foundation) — standard single-page menu

### Foundation Menu System
```
Menu (abstract)
  ├── MenuStandard — fixed layout menu
  ├── MenuPagged<T> — auto-paginated list menu
  ├── MenuQuantifiable — number selection
  └── MenuContainerChances — weighted random items
```

### Menu Lifecycle
```
Player clicks command/item
  → Menu.displayTo(player)
    1. Create inventory (size, title)
    2. Call getItemAt(slot) for each slot
    3. Register click listeners
    4. Show to player

Player clicks slot
  → onMenuClick(player, slot, action, clickedItem)
    1. Cancel vanilla click (prevent item removal)
    2. Process custom logic
    3. Update display or close
```

## Color Menu

### Purpose
Allows players to pick chat color and decoration (bold, italic, etc.) from a visual GUI.

### Implementation
```java
public class ColorMenu extends MenuPagged<CompChatColor> {
    // Shows wool blocks colored to match available chat colors
    // Each color requires permission: chatcontrol.color.<name>
    // Clicking sets the player's chat color preference
}
```

### Color Menu Features
- **Wool/carpet items** represent each color
- **Color names** shown on item tooltips
- **Permission-based**: only shows colors player has access to
- **Pagination**: auto-pages if many colors available
- **Decorations**: bold, italic, underline, strikethrough, magic
- **Preview**: shows example text in chosen color

### Color Permissions
- `chatcontrol.color.<color_name>` — access to specific color
- `chatcontrol.color.*` — all colors
- `chatcontrol.color.bold`, `.italic`, `.underline`, `.strikethrough`, `.magic` — decorations
- `chatcontrol.color.hex` — custom hex colors
- `chatcontrol.color.gradient` — gradient colors

### Color Command
- `/color` or `/chatcolor` — opens the color picker menu
- Aliases configurable in settings.yml
- Permission: `chatcontrol.command.color`

## Spy Menu

### Purpose
Allows staff to toggle which message types they want to spy on.

### Implementation
```java
public class SpyMenu extends MenuStandard {
    // Toggle buttons for each spy type:
    // - Chat messages
    // - Private messages
    // - Commands
    // - Channel messages
    // - Sign edits
    // - Book edits
    // - Anvil renames
}
```

### Spy Types
| Type | Description | Toggle Item |
|------|-------------|-------------|
| `CHAT` | Public chat messages | Chat bubble icon |
| `PRIVATE_MESSAGE` | PM conversations | Letter icon |
| `COMMAND` | Player commands | Command block icon |
| `CHANNEL` | Channel messages | Channel icon |
| `SIGN` | Sign text edits | Sign icon |
| `BOOK` | Book content edits | Book icon |
| `ANVIL` | Anvil rename text | Anvil icon |

### Spy Permissions
- `chatcontrol.spy.<type>` — spy on specific type
- `chatcontrol.spy.*` — spy on everything
- `chatcontrol.command.spy` — access spy menu

### Spy Command
- `/spy` or `/chc spy` — opens spy toggle menu
- `/spy <type>` — toggle specific type directly

## Channel Menu

### Purpose
Browse and join available channels from a GUI.

### Features
- Lists all channels player has permission to see
- Shows current channel with glow effect
- Click to join/leave channels
- Shows channel description and player count

## Foundation Menu Framework

### Creating Custom Menus
```java
public class MyMenu extends Menu {
    @Override
    protected String[] getInfo() {
        return new String[]{"Menu description line 1", "Line 2"};
    }

    @Override
    public ItemStack getItemAt(int slot) {
        if (slot == 13)
            return ItemCreator.of(CompMaterial.DIAMOND)
                .name("&bClick me!")
                .lore("&7Description here")
                .make();
        return null;
    }

    @Override
    protected void onMenuClick(Player player, int slot, 
            InventoryAction action, ClickType click, 
            ItemStack cursor, ItemStack clicked, boolean cancelled) {
        if (slot == 13) {
            player.sendMessage("You clicked the diamond!");
            // restartMenu() to refresh, or closeMenu()
        }
    }
}
```

### Menu Features (Foundation)
| Feature | Description |
|---------|-------------|
| `getSize()` | Inventory rows (9, 18, 27, 36, 45, 54) |
| `getTitle()` | Inventory title |
| `getInfo()` | Info button content (slot 0 by default) |
| `addReturnButton()` | Back button to parent menu |
| `newInstance()` | Required for menu refresh |
| `restartMenu()` | Refreshes menu content |
| `animateTitle(String)` | Animated title text |

### Paged Menus
```java
public class ListMenu extends MenuPagged<MyItem> {
    public ListMenu(Iterable<MyItem> items) {
        super(items);
    }

    @Override
    protected ItemStack convertToItemStack(MyItem item) {
        return ItemCreator.of(item.getMaterial())
            .name(item.getName())
            .make();
    }

    @Override
    protected void onPageClick(Player player, MyItem item, ClickType click) {
        // Handle click on a list item
    }
}
```

- Auto-pagination with previous/next buttons
- Configurable items per page
- Page number display

## Common Issues & Solutions

### "Menu not opening"
1. Check command permission (`chatcontrol.command.color`, `chatcontrol.command.spy`)
2. Check for console errors on menu open
3. Inventory size must be multiple of 9 (max 54)
4. Title max 32 chars (pre-1.20) or longer (1.20+)

### "Menu items missing"
1. Check player has required permissions for items
2. Paged menu: check if items are on another page
3. Color menu: player needs `chatcontrol.color.<color>` permissions
4. Spy menu: player needs `chatcontrol.spy.<type>` permissions

### "Clicking does nothing"
1. Menu may need `restartMenu()` call to update
2. Check for errors in console on click
3. Verify click handler slot numbers match item positions
4. Inventory click event might be cancelled by another plugin

### "Items can be taken from menu"
- Foundation menus cancel clicks by default
- If items are removable, another plugin may be interfering
- Check for conflicting inventory event listeners

### "Menu flickers/refreshes constantly"
- Animated title updates cause brief flicker
- `restartMenu()` in a loop causes rapid refreshing
- Use targeted slot updates instead of full restart when possible

## Key File Paths

- ColorMenu: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/menu/ColorMenu.java`
- SpyMenu: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/menu/SpyMenu.java`
- Menu (Foundation): `Foundation/foundation-bukkit/src/main/java/org/mineacademy/fo/menu/Menu.java`
- MenuPagged: `Foundation/foundation-bukkit/src/main/java/org/mineacademy/fo/menu/MenuPagged.java`
- MenuStandard: `Foundation/foundation-bukkit/src/main/java/org/mineacademy/fo/menu/MenuStandard.java`
- ItemCreator: `Foundation/foundation-bukkit/src/main/java/org/mineacademy/fo/menu/model/ItemCreator.java`
- Settings: `chatcontrol-bukkit/src/main/resources/settings.yml` (Color, Spy sections)
