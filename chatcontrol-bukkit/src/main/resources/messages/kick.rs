# ---------------------------------------------------------------------------------
# Kick messages. Uses the same syntax as files in rules/ folder but operators
# are slightly different. For documentation and a quick tutorial, see:
# https://github.com/kangarko/ChatControl/wiki/messages
#
# Files in messages/ folder are read from top to bottom. Using a true value in your settings file,
# in the 'Stop_On_First_Match' part means that if you have multiple messages there,
# we only send the first eligible message to the player (we evaluate
# this for each player separatedly).
# A false value means players will see all messages that are eligible for them. 
#
# WARNING: These messages are unlikely to be used if you have a ban management
# plugin.
#
# Rules in this file are read from top to bottom. Set 'Stop_On_First_Match' 
# key in settings.yml only send the first message we can, to the player (based on
# operator conditions).
# ---------------------------------------------------------------------------------

group default
message:
- <gray>{player} <white>has been kicked from the game
