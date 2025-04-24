# ---------------------------------------------------------------------------------
# Timed messages broadcaster. Uses the same syntax as files in rules/ folder but
# operators are slightly different. For documentation and a quick tutorial, see:
# https://docs.mineacademy.org/chatcontrol/messages
#
#
# They are read like a newspaper and each player only sees one message,
# that is the first one we can send to him. For more informations, please
# refer to the `Stop_On_First_Match` part of the settings file.
# ---------------------------------------------------------------------------------

# You can run limited time offers and even make clickable links by referencing
# to a format from /formats in your message. The formatting can also make the
# message have clickable/hoverable elements.
#group special
#expires 31 Dec 2024, 15:00
#then sound ENTITY_ARROW_HIT_PLAYER, 1.0, 0.1
#delay 6 seconds
#message:
#- special-offer

# This group will simply send messages everywhere.
#group global
#delay 1 second
#message:
#- Hey, {player}, did you know that we are running ChatControl?
#- Check out <green>https://mineacademy.org/plugins

# This group will send messages only to the given worlds.
#group hardcore
#require sender world hardcore|hardcore_nether|hardcore_end
#message:
#- Grief is not permitted what-so-ever and every griefer will be banned.
#- Can you survive the night on {world} world?

# This group will only send messages to the creative world.
#group creative
#require sender world creative
#message:
#- Welcome on Creative world. Enjoy your gamemode :)
#- This is an example of multi-line.
#  Use it if the new '\ and n' character
#  is not working on your server.
#- This is another message, this time only on one line!

# You can also send raw JSON messages using this example here:
# WARNING: Requires Minecraft 1.16+. For older versions, generate
# a new JSON at https://www.minecraftjson.com/ and set MC to 1.13
#group json-sample
#delay 600 seconds
#prefix [JSON]
#message: 
#- ["",{"text":"Please see ","color":"yellow"},{"text":"/buy ","color":"#FF0000","clickEvent":{"action":"run_command","value":"/buy"},"hoverEvent":{"action":"show_text","contents":[{"text":"Click here to run the ","color":"gold"},{"text":"/buy ","bold":true,"color":"light_purple"},{"text":"command!","color":"gold"}]}},{"text":"to purchase ranks!","color":"yellow"}]
