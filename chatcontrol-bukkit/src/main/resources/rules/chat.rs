# -----------------------------------------------------------------------------------------------
# This file applies rules to chat messages and includes rules from global.rs.
#
# For help, see https://docs.mineacademy.org/chatcontrol/rules
# -----------------------------------------------------------------------------------------------

@import global

# -----------------------------------------------------------------------------------------------
# Anti spam.
# -----------------------------------------------------------------------------------------------

# Restrict one same character to max. two repeats. Ignore minimessage tags and numbers.
# Example: aaaaaaaaaa to aa
#match ([^\d])(?=\1\1+(?![^<]*>))
# Prevent using backslash to bypass restricted minimessage tags.
#ignore string \\
#then replace

# Restrict two same characters to max. two repeats. Ignore minimessage tags and numbers.
# Example: hahahaha to haha
#match (\D\D)(?=\1\1+(?![^<]*>))
#then replace

# Restrict three same characters to max. two repeats. Ignore minimessage tags and numbers.
# Example: lollollol to lollol
#match (\D\D\D)(?=\1\1+)
#then replace

# Prevent special, unicode characters. Those are misused to bypass filters.
# Please keep in mind that non-English languages use them, so only uncomment this on English servers.
# [WARNING] You must set Rules.Unicode to true in settings.yml for this to work.
#match [^\u0000-\u007F]+
#then warn {prefix_error} Unicode characters are prohibited.
#then deny

# Prevent hacked clients spamming messages with UUIDs in them: https://i.imgur.com/zuaROdw.png
#match (\[|)\b[0-9a-f]{8}(\b-|)[0-9a-f]{4}(-|)[0-9a-f]{4}(-|)[0-9a-f]{4}(-\b|)[0-9a-f]{12}(\]|)
#then warn {prefix_error} Please do not type UUIDs into chat.
#then deny

# -----------------------------------------------------------------------------------------------
# Prevent vanish users from chatting in public channels when vanished
# -----------------------------------------------------------------------------------------------

# Makes it so vanished users cannot talk unless they are in the
# admin channel or use "%" at the start of their message in chat
#match ^[^%]
#require variable {player_vanished}
#ignore channel admin
#then warn {prefix_error} You are vanished!
#then warn {prefix_error} Your message must start with <gold>%</gold> to talk in chat
#then deny
#dont verbose

# This removes the "%" from the start of a vanished
# users message when they talk in chat.
#match ^[%]
#require variable {player_vanished}
#ignore channel admin
#strip colors false
#strip accents false
#then replace
#dont verbose

# -----------------------------------------------------------------------------------------------
# Forwarding messages to other channels.
# -----------------------------------------------------------------------------------------------

# Uncomment to forward any message starting with ! to proxy channel
#match ^[!](.+)
#then command ch send proxy $1
#strip colors false
#strip accents false
#dont verbose
#then deny

# Uncomment to forward any message starting with . to admin channel
#match ^[.](.+)
#then command ch send admin $1
#strip colors false
#strip accents false
#dont verbose
#then deny

# -----------------------------------------------------------------------------------------------
# An example of automatically switching channels depending on player's world when he chats.
# You may also want to go into your localization file and hide these messages by setting 
# Channels.Join sections to '' for keys "Success", "Leave_Reading" to prevent message spam
# when channels are automatically switched.
# -----------------------------------------------------------------------------------------------

# Switches channel for user if not in lobby_chat channel while in the lobby world
#match (.*)
#require world lobby_world
#ignore channel lobby_chat
#then console channel join lobby_chat write {player}|channel sendas {player} lobby_chat $0
#strip colors false
#strip accents false
#then deny

# Switches channel for user if not in survival_chat channel while in a survival world
#match (.*)
#require world survival_world|survival_world_nether|survival_world_the_end
#ignore channel survival_chat
#then console channel join survival_chat write {player}|channel sendas {player} survival_chat $0
#strip colors false
#strip accents false
#then deny

# Switches channel for user if not in creative_chat channel while in a creative world
#match (.*)
#require world creative_world
#ignore channel creative_chat
#then console channel join creative_chat write {player}|channel sendas {player} creative_chat $0
#strip colors false
#strip accents false
#then deny

# -----------------------------------------------------------------------------------------------
# Grammar corrections and fun replacements.
# Credit to Tom from Piratecraft (piratemc.com)
# -----------------------------------------------------------------------------------------------

#match \bdis\b
#then replace this

#match \bwanna\b
#then replace want to

#match \bgonna\b
#then replace going to

#match \bu\b
#then replace you

#match \bdia\b
#then replace diamond

#match ^gg$
#then replace Good Game!

#match ^np$
#then replace No Problem!

#match ^omg$
#then replace My golly, this is qwite the shock OwO

#match ^wtf$
#then replace This princessy-wessy is qwite confuzzled

#match ^(hello|hi|sup)$
#then replace Greetings from afar fellow pwincesses, this pwincess is looking for some love and compassion

#match ^no$
#then replace noh :3

#match ^yes$
#then replace ow yeah :3

#match \bcya$
#then replace bon voyage, me princess

#match \boml$
#then replace >.< i iz confuzzled i need some hewp appwreciating dis fwilter

#match \bcannon\b
#then replace shooty-wooty

# -----------------------------------------------------------------------------------------------
# Educate players.
# -----------------------------------------------------------------------------------------------

# Tell people how to do basic things on your server.
#match ^how (do|can) (I|you) (build|claim)(| a) (land|residence)
#then warn {prefix_info} Use <gold><hover:show_text:'<gray>Click to execute'><click:run_command:'/help land'>/help land</click></hover></gold> to learn about claiming land!
#then deny

# Prevent spamming "lag" and give advice instead.
#match \bl+\s*a+\s*g+
#then warn {prefix_warn} If you believe there is a lag, check your <gold><hover:show_text:'<gray>Click to execute'><click:run_command:'/ping'>/ping</click></hover></gold> first. Anything over 300 is too slow! See this video for client optimization: <click:open_url:'https://www.youtube.com/watch?v=w97r96GAosg'>https://www.youtube.com/watch?v=w97r96GAosg</click>
#then deny

# -----------------------------------------------------------------------------------------------
# Prevent begging for ranks.
# -----------------------------------------------------------------------------------------------

#match (can|may|would you like if) i (have|be|become|get|has) (op|admin|mod|builder)
#then warn {prefix_info} Currently, we are not looking for new staff.
#then deny

#match (do|are) you (need|wish|looking for) (any|some|one|good) (op|ops|operators|admins|mods|builders|new people|ateam)
#then warn {prefix_info} Currently, we are not looking for new staff.
#then deny

# -----------------------------------------------------------------------------------------------
# Prevent people saying bad things about your server.
# -----------------------------------------------------------------------------------------------

#match this server (is (bad|crap|shit)|suck)
#name server hate
#then rewrite I love this server!|I can't behave property due to brain damage!|My bad manners was corrected by server.
#then notify chatcontrol.notify.rulesalert <dark_gray>[<gray>ID {rule_name}<dark_gray>] <gray>{player}: <white>{original_message}
#then console kick {player} <red>Your rating will be processed by our staff soon. \nThanks and welcome back!

#match ((admin|op|ateam|server|owner) (is|are)) +(a|) *(dick|cock|duck|noob)
#name server hate
#then console kick {player} <red>I don't think so.
#then deny

# -----------------------------------------------------------------------------------------------
# Simple chat bots.
# You can then use the {player_data_name} variable anywhere else, also in PlaceholderAPI!
# -----------------------------------------------------------------------------------------------

# You can create simple helping bots to answer frequently asked questions.
# This will simply listen to the question below and then send player formats/sethome.yml message
#match ^how (do|can|to)(| I| you) (set|create|place)(| a) home
#then warn sethome
#then deny

# Or you can create advanced bots saving and showing data (data is saved permanently)
# See https://docs.mineacademy.org/chatcontrol/rules for a tutorial
#match ^(\@bot name)$
#ignore key name
#then warn <dark_gray>[<light_purple>Bot<dark_gray>] <gray>Please enter your name.
#then deny

#match ^(\@bot name)$
#require key name
#then warn <dark_gray>[<light_purple>Bot<dark_gray>] <gray>Your name is: {player_data_name}
#then deny

#match ^(\@bot name) null$
#save key name
#then warn <dark_gray>[<light_purple>Bot<dark_gray>] <gray>Removed your name.
#then deny

#match ^(\@bot name)(.*)
#save key name "$2".trim()
#then warn <dark_gray>[<light_purple>Bot<dark_gray>] <gray>Saved your name as: {player_data_name}.
#then deny

# -----------------------------------------------------------------------------------------------
# An example of overloading rules.
# Both rules will match "@health" in chat, but only one will fire at the time thanks to
# javascript conditions. So player will receive one message about his health status.
# -----------------------------------------------------------------------------------------------

#match @health
#require script {player_health} > 15
#then warn <green>You are healthy!
#then deny

#match @health
#require script {player_health} <= 15
#then warn <red>You are damaged!
#then deny

# -----------------------------------------------------------------------------------------------
# Turn commands in the chat to red, i.e. colorizing the /help in "Enter /help to get it".
# Using MiniMessage tags, the color will be automatically reverted to whatever it was before.
# Notice the usage of space after the <red> because the matcher will replace the initial one.
# -----------------------------------------------------------------------------------------------

#match \s\/\w+
#then replace <red> $0</red>

# -----------------------------------------------------------------------------------------------
# Smileys (some turds call them emojis)
#
# Notice this may or may not work on your system. Ensure you save the file in UTF-8 encoding,
# and if it still does not work, do not report this, ask your server administrator/hosting.
# Credit to Tom from Piratecraft (piratemc.com)
# -----------------------------------------------------------------------------------------------

# You can also use MiniMessage tags to colorize those.
#match \:rainbowcat\:
#then replace <gradient:red:#f79459>(=^･ｪ･^=))ﾉ彡☆</gradient>

#match :-\(
#then replace ☹

#match (:\))|(;\))
#then replace ㋡

#match \:star\:
#then replace ★

#match \:shrug\:
#then replace ¯\\_(ツ)_/¯

#match \:flip\:
#then replace (╯°□°）╯︵ ┻━┻

# This won't work if you caught "wtf" above, unless you put it before it.
#match \:wtf\:
#then replace ⚆_⚆

#match \:derp\:
#then replace (◑‿◐)

#match \:love\:
#then replace (✿ ♥‿♥)

#match \:sad\:
#then replace (ಥ﹏ಥ)

#match \:finger\:
#then replace ╭∩╮ ( •_• ) ╭∩╮

#match \:peace\:
#then replace ✌(-‿-)✌

#match \:gun\:
#then replace ︻╦╤─

#match \:butterfly\:
#then replace ƸӜƷ

#match \:tick\:
#then replace <green>✓<reset>

#match \:dead\:
#then replace x⸑x

#match \:fu\:
#then replace ┌П┐(ಠ_ಠ)

#match \:haha\:
#then replace ٩(^‿^)۶

#match \:meep\:
#then replace \(°^°)/

#match \:meh\:
#then replace ಠ_ಠ

#match \:no\:
#then replace →_←

#match \:nyan\:
#then replace ~=[,,_,,]:3

#match \:omg\:
#then replace ◕_◕

#match \:cat\:
#then replace ฅ^•ﻌ•^ฅ

#match \:shy\:
#then replace =^_^=

#match \:smirk\:
#then replace ¬‿¬

#match \:unflip\:
#then replace ┬──┬ ノ(ò_óノ)

#match \:up\:
#then replace ↑

#match \:whistle\:
#then replace (っ^з^)♪♬

#match \:wut\:
#then replace ⊙ω⊙

#match \:yay\:
#then replace \( ﾟヮﾟ)/

#match \:rip\:
#then replace rest in spaghetti never forgetti
