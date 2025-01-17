# ---------------------------------------------------------------------------------
# Quit messages. Uses the same syntax as files in rules/ folder but operators
# are slightly different. For documentation and a quick tutorial, see:
# https://github.com/kangarko/ChatControl/wiki/messages
# ---------------------------------------------------------------------------------

# Sent to all players on the network.
# Admins with "chatcontrol.silence.leave" won't have a leave message.
group default
ignore sender perm chatcontrol.silence.leave
prefix &8[&c-&8]
message:
- &e{player} &fleft the network &7({server_name})

# Message sent if the player bypassed the group above.
# Only shown to players with the "chatcontrol.bypass.silence.leave" perm. 
group bypass_default
require receiver perm chatcontrol.bypass.silence.leave
require sender perm chatcontrol.silence.leave
prefix &8[&c-&8]
message:
- &e{player} &fleft the network &7({server_name})