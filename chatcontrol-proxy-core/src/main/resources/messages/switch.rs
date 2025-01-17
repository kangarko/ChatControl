# ---------------------------------------------------------------------------------
# Join messages. Uses the same syntax as files in rules/ folder but operators
# are slightly different. For documentation and a quick tutorial, see:
# https://github.com/kangarko/ChatControl/wiki/messages
# ---------------------------------------------------------------------------------

# Message sent to all players on the network.
# Players with "chatcontrol.silence.switch" won't have a switch message.
group main_message
ignore sender perm chatcontrol.silence.switch
prefix &8[&d>&8]
message:
- &e{player} &fhas switched from &7{from_server} &fto &7{to_server}

# Message sent if the player bypassed the group above.
# Only shown to players with the "chatcontrol.bypass.silence.switch" perm.
group bypass_main_message
require receiver perm chatcontrol.bypass.silence.switch
require sender perm chatcontrol.silence.switch
prefix &8[&d>&8]
message:
- &e{player} &fhas switched from &7{from_server} &fto &7{to_server}