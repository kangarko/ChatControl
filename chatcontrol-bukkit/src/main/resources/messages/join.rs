# ---------------------------------------------------------------------------------
# Join messages. Uses the same syntax as files in rules/ folder but operators
# are slightly different. For documentation and a quick tutorial, see:
# https://docs.mineacademy.org/chatcontrol/messages
#
# Rules in this file are read from top to bottom. Set 'Stop_On_First_Match' 
# key in settings.yml only send the first message we can, to the player (based on
# operator conditions).
# ---------------------------------------------------------------------------------


# An example of First Join Message for 1.13+ versions. Uncomment the lines below if your server runs MC 1.13 or greater.
# Please make sure to keep your first join message at the top of the file, above any other join message to avoid any issues.
# THE NAME IS MISLEADING, IT STILL RECORDS TICKS DESPITE IT SAYING MINUTES
#group firstjoinmessage
#require sender script player.getStatistic(Java.type("org.bukkit.Statistic").PLAY_ONE_MINUTE) <= 100
#message: 
#- <gray>Welcome to the server <red>{player}<gray>!

# An example of First Join Message for 1.7 to 1.12. Uncomment the lines below if your server runs MC 1.12 or older.
# Please make sure to keep your first join message at the top of the file, above any other join message to avoid any issues.
#group firstjoinmessage
#require sender script player.getStatistic(Java.type("org.bukkit.Statistic").PLAY_ONE_TICK) <= 100
#message: 
#- <gray>Welcome to the server <red>{player}<gray>!

# An example of custom join message based on the playername
# ( This is case sensitive )
#group kangarko-join-message
#require sender script "{player}" == "Kangarko"
#message: 
#- <red>Kangarko <gray>joined your server. No, he's not a kangaroo

# An example of custom join message based on a permission.
# Players/ranks with the permission 'group.vip' will use this message instead of the default one
# (Only if 'Stop_On_First_Match' is set to 'true' in the settings file)
#group vip-join-message
#require sender perm group.vip
#message: 
#- <yellow>[VIP] <red>{player} <gray>joined the server

group default
message:
- <gray>{player} <white>joined the server
