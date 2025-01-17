# ---------------------------------------------------------------------------------
# Join messages. Uses the same syntax as files in rules/ folder but operators
# are slightly different. For documentation and a quick tutorial, see:
# https://github.com/kangarko/ChatControl/wiki/messages
# ---------------------------------------------------------------------------------

# Only sent when the player joins for the first time.
# The newcomer status is not changed until the player disconnects, so you can continue
# using this and the "require playedbefore" operator in switch/quit messages also.
group newcomer
ignore playedbefore
prefix &8[&d+&8]
message:
- &6{player} joined for the first time

# Sent to all players on the network.
# Player with "chatcontrol.silence.join" won't have a join message.
group default
ignore sender perm chatcontrol.silence.join
prefix &8[&a+&8]
message:
- &e{player} &fjoined the network &7({server_name})

# Message sent if the player bypassed the group above.
# Only shown to player with the "chatcontrol.bypass.silence.join" perm.
group bypass_default
require receiver perm chatcontrol.bypass.silence.join
require sender perm chatcontrol.silence.join
prefix &8[&a+&8]
message:
- &e{player} &fjoined the network &7({server_name})