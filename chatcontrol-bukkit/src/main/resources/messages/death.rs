# ---------------------------------------------------------------------------------
# Death messages. Uses the same syntax as files in rules/ folder but operators
# are slightly different. For documentation and a quick tutorial, see:
# https://docs.mineacademy.org/chatcontrol/messages
#
# Rules in this file are read from top to bottom. Set 'Stop_On_First_Match' 
# key in settings.yml only send the first message we can, to the player (based on
# operator conditions).
# ---------------------------------------------------------------------------------

# They are read like a newspaper and each player only sees one message,
# that is the first one we can send to him.

# An example of not sending any message when a player kills an NPC player
group citizens-npc
require npc
then abort

# An example of not sending any message when player dies having creative gamemode
group silent_on_creative
require sender gamemode CREATIVE
then abort

# Require the player to by killed by an arrow.
group playerArrow
require projectile arrow
require killer player
message:
- {player} has been murdered by {killer}'s {killer_item}

# Require the player to by killed by a trident.
#group playerTrident
#require projectile trident
#require killer player
#message:
#- {killer} has thrown {killer_item} at {player}

# Require the player to be killed by himself throwing an enderpearl.
group enderPearl
require self
require cause fall
require projectile ender_pearl
message:
- {player} has died by ender pearl

# Support MythicMobs or Boss plugin and send a special death message.
group boss
require boss *
message:
- {player} has died by a Boss {boss_name}

# If no group from above applies, fall back and send a default message.
group default
# You can also broadcast this over proxy
#proxy
then log {player} has died at {world} {x} {y} {z} by {cause}
message:
- {player} has died by unknown forces
# Or you can just show the one from Minecraft
#- {original_message}