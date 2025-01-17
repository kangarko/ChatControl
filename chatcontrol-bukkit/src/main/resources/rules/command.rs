# -----------------------------------------------------------------------------------------------
# This file applies rules to commands and includes rules from global.rs.
#
# For help, see https://github.com/kangarko/ChatControl/wiki/Rules
# -----------------------------------------------------------------------------------------------

@import global

# -----------------------------------------------------------------------------------------------
# Prevent some abused game commands.
# -----------------------------------------------------------------------------------------------

# Prevent /op command but ignore other commands by ending the message with \b.
# This makes the filter match /op but not /open. The \b means basically a space after the word.
#match ^[/]op\b
#ignore perm chatcontrol.bypass.rules
#name /op
#then warn {prefix_success} Yes, we are giving away OPs. Seriously, what did you expect?!
#then notify chatcontrol.notify.rulesalert <dark_gray>[<gray>ID {rule_name}<dark_gray>] <gray>{player}: <white>{original_message}
#then deny

# Prevent /gm and /gamemode command without permission, replacing error with a funny message.
#match ^[/](gm|gamemode)\b
#ignore perm essentials.gamemode
#name /gm
#then warn {prefix_success} That would be nice, wouldn't it?
#then notify chatcontrol.notify.rulesalert <dark_gray>[<gray>{server_name}<dark_gray>] <dark_gray>[<gray>ID {rule_name}<dark_gray>] <gray>{player}: <white>{original_message}
#then deny

#match ^[/]heal\b
#ignore perm essentials.heal
#then warn {prefix_question} Game too hard for ya?
#then deny

#match ^[/]trade\b
#ignore gamemode survival
#then warn {prefix_error} Nah, you can't trade your hard earned creative diamonds, fool!
#then deny

# -----------------------------------------------------------------------------------------------
# Create "/admin toggle" command that switches player from writing in admin and standard channel.
# -----------------------------------------------------------------------------------------------

# This rule only executes when player is not in the admin channel for reading.
#match ^([/]admin toggle)
#dont verbose
#ignore channel admin write
#then command channel join admin write
#then deny

# This rule only executes when player is in the admin channel for writing.
#match ^([/]admin toggle)
#dont verbose
#require channel admin write
#then command channel join standard write
#then deny

# -----------------------------------------------------------------------------------------------
# Create a /helpop command that sends messages to a channel.
# -----------------------------------------------------------------------------------------------

# Catch "/helpop" command and send usage.
#match ^[/]helpop$
#dont verbose
#then warn <red>Description: Send a message to online staff members.
#then warn <red>Usage: /helpop <message>
#then deny

# Catch "/helpop <message>" and redirect to the helpop channel.
#match ^([/]helpop) (.*)
#dont verbose
#strip colors false
#strip accents false
#then command channel send helpop $2
#then deny
 
# -----------------------------------------------------------------------------------------------
# Create a /nick command that executes a longer /tag nick command.
# -----------------------------------------------------------------------------------------------

# Create a simple alias for /tag nick to just type /nick instead, using two rules.
# This rule only sends the usage message, and the rule below actually forwards the command.
#match ^[/]nick$
#require perm nick.set.own <red>Insufficient permission ({permission}).
#then warn <red>Description: Sets your own nickname.
#then warn <red>Usage: /nick <nick>
#then deny

# An example used for /nick command alias with the help of group matching (see the $2), it will
# replace what players type in /nick - that is pulled from the (.*) part
#match ^[/]nick (.*)
#require perm nick.set.own <red>Insufficient permission ({permission}).
#then command tag nick $1
#strip colors false
#strip accents false
#then deny

# -----------------------------------------------------------------------------------------------
# Create new commands and educate players.
# -----------------------------------------------------------------------------------------------

# Prevent sending mails directly to staff members and forward them to appropriate channel.
# This obviously requires you having /helpop system, it will not work if you don't have one.
# TIP: You can use channels to create a helpop channel, and create a rule that forwards
# all /helpop messages into such channel, all using just ChatControl. 
#match ^[/]mail send (kangarko|anotheradmin)\b
#then warn {prefix_warn} Please use <gold>/helpop</gold> to contact our staff.
#then deny

# Typing /help will call the "help" format showing players your help.
# You can also match multiple pages of help by creating multiple rules such as ^/help 2$
# and formats such as help-2 and design your entire help system using ChatControl! 
#match ^[/]help$
#then warn help
#then deny

# You can create new in-game commands such as /ping that is using a custom ping variable
# from the /variables folder.
# KEEP IN MIND THAT THE PING VARIABLE MIGHT ACCESS NMS AND POSES A PERFORMANCE PENALTY
#match ^[/]ping$
#then warn {prefix_info} Your ping is: {player_ping}ms
#then deny

# -----------------------------------------------------------------------------------------------
# Example usage of group matching.
# -----------------------------------------------------------------------------------------------

# Regular expression will make multiple group matches, in this case
# (prefix) is $1 (first) and (.*) is $2 (second)
# You can refer to them in the messages.
# NOTICE: YOU CAN ALSO USE CHATCONTROL'S TAG SYSTEM TO SET A PREFIX VIA /TAG
#match ^[/](prefix) (.*)
#require perm prefix.set.own <red>Insufficient permission to change prefix. ({permission})
#then console pex user {player} prefix $2
#then warn Your prefix is now: $2
#strip colors false
#strip accents false
#then deny
