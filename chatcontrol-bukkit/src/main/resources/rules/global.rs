# -----------------------------------------------------------------------------------------------
# Welcome to the main rules configuration file
# -----------------------------------------------------------------------------------------------
#
# Rules are regular expressions tested against every message and, when matched, they can execute
# numerous operators you define.
#
# The rules/ folder contains files for all aspects of the game, with rules in the global file
# being applied for everything.
#
# We apply rules from top to bottom order.
#
# You can remove/edit everything in rules/ as you wish.
#
# Credits to TheIntolerant for greatly improving the filters
# -----------------------------------------------------------------------------------------------

# IP filter.
# Keeps all obfuscation separators advertisers/bypassers use (. , - : ; space "dot" "(dot)"),
# but validates each octet is 0-255. The octet check alone eliminates dates (2026-04-20-09),
# phone numbers (555-123-456-789) and most version numbers from matching, with zero loss of
# real advertising coverage (1,2,3,4 / 1-2-3-4 / 1:2:3:4 / 1;2;3;4 / 1 2 3 4 / 1 dot 2 dot 3 dot 4 all still caught).
match \b(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])([ \t]*(\.|dot|\(dot\)|-|;|:|,)[ \t]*(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}\b
name ip
group advertisement
# Ignore common Minecraft context words that precede 4-number tuples in legitimate chat
# (coords, scores, IDs). Advertisers paste IPs without these framing words.
ignore string \b(coord|coords|coordinate|coordinates|tp|teleport|pos|position|loc|location|spawn|warp|x\s*=|y\s*=|z\s*=|ratio|score|scores|stats|uid|id\s*:|version|updated|update|upgrade|upgraded|paper|spigot|bukkit|folia|velocity|bungee|plugin|mod|mc|build|buying|selling|buy|sell|trade|trading|price|each|per|offer)\b

# Domain/URL filter.
match (?:(?<![0-9])[a-zA-Z][a-zA-Z0-9\-\.\*]*|[0-9][a-zA-Z0-9\-\.\*]{2,}(?<![0-9][bslfdBSLFD]))\s?(\.|\*|dot|\(dot\)|-|\(\*\)|;|:|,)\s?(c(| +)o(| +)m|o(| +)r(| +)g|n(| +)e(| +)t|(?<! )c(| +)z|(?<! )c(| +)o|(?<! )u(| +)k|(?<! )s(| +)k|b(| +)i(| +)z|(?<! )m(| +)o(| +)b(| +)i|(?<! )x(| +)x(| +)x|(?<! )e(| +)u|(?<! )m(| +)e|(?<! )i(| +)o|(?<! )o(| +)n(| +)l(| +)i(| +)n(| +)e|(?<! )x(| +)y(| +)z|(?<! )f(| +)r|(?<! )b(| +)e|(?<! )d(| +)e|(?<! )c(| +)a|(?<! )a(| +)l|(?<! )a(| +)i|(?<! )d(| +)e(| +)v|(?<! )a(| +)p(| +)p|(?<! )i(| +)n|(?<! )i(| +)s|(?<! )g(| +)g|(?<! )t(| +)o|(?<! )p(| +)h|(?<! )n(| +)l|(?<! )i(| +)d|(?<! )i(| +)n(| +)c|(?<! )u(| +)s|(?<! )p(| +)w|(?<! )p(| +)r(| +)o|(?<! )t(| +)v|(?<! )c(| +)x|(?<! )m(| +)x|(?<! )f(| +)m|(?<! )c(| +)c|(?<! )v(| +)i(| +)p|(?<! )f(| +)u(| +)n|(?<! )i(| +)c(| +)u)\b
# Ignore the "/chc:me" in "/chc:me hello world" and only test this rule against "hello world" to prevent false catches.
ignore commandprefix
# If you want to ignore your server, put it at the end of ignore string below like: minecraft.net|youtube.com|yourserver.com
ignore string minecraft.net|youtube.com|imgur.com
# Ignore English compounds and programming identifiers that look like "word.in" / "word.to" / "word.is" / "word.me" etc.
# These are legit chat ("please log in", "System.in", "String.to") — not domains. Extend as needed.
ignore string \b(log|sign|opt|plug|check|walk|break|listen|type|come|fill|pop|drop|stand|tune|fit|cash|lock|kick|punch|hand|roll|pull|push|give|move|step|turn|set|jump|dig|dive|ring|call|run|close|fold|hang|knock|lean)\s*\.\s*in\b|\b(string|system|path|file|context|class|object|method|integer|boolean|double|float|long|list|map|set|stream|array|thread|process|socket|buffer|reader|writer|scanner|random)\s*\.\s*(in|to|is|as|gg|tv|cc|me|co|pw|us|id|fm|mx|cx|ca|ai|be|de|fr|al|eu|nl|ph|sk|uk)\b
# You can also uncomment this operator to allow email addresses. Credit: http://emailregex.com/
#ignore string (?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])
name url
group advertisement

# Here is the previous version of the domain filter above which is easier to modify but does not block: t e s t . c o m, etc.
# You can add new domains to be blocked by inserting '|your_domain' after '|me' (e.g. '|me|io' for also blocking '.io' domains)
#match [a-zA-Z0-9\-\.]+\s?(\.|dot|\(dot\)|-|;|:|,)\s?(com|org|net|cz|co|uk|sk|biz|mobi|xxx|eu|me|io)\b

# -----------------------------------------------------------------------------------------------
# Predefined swear & content filters are found below. Credits to owenmaple for contributing.
#
# [TIP] To match swear words with whitespace inside, like 'f u c k'
# add the following line under 'match (..)': before strip \s*
# -----------------------------------------------------------------------------------------------

# Swastika filter
match \u534D|\u5350
then kick <red>Disallowed characters in the chat
then notify chatcontrol.notify.rulesalert <dark_gray>[<gray>Swastika<dark_gray>] <gray>{player}: <white>{original_message}
then deny

# Bitch filter - commented out just to show you example of usage. It is filtered in more advanced way below.
#match \bb[i_!@#$%^&*]tch
#name swear
#then warn <red>Watch your language please.
# Line below requires warning points enabled in settings.yml
#then points swear 1
#then notify chatcontrol.notify.rulesalert <dark_gray>[<gray>{rule_name}<dark_gray>] <gray>{player}: <white>{original_message}
# You can also use MiniMessage tags and {matched_message} to get the matched part of the message.
#then replace <hover:show_text:'<red>Swearing on this server\nis prohibited and will get you banned.\n\n<gray>Click this to appeal.'><click:suggest_command:'/appeal My message got censored: "$0"'><red>[censored]</red></click></hover>

# Blocks 'fuck', 'f#ck' and 'f*ck' etc. Also catches 'fucker', 'fucking', 'motherfucker', 'clusterfuck' etc.
match \b\w*f+[\W\d_]*[u_!@#$%^&*]+[\W\d_]*c+[\W\d_]*k+\w*\b
group swear

# Need more swear rules but do not have time to write them manually?
# 1. Purchase our addon pack, that covers all commonly used English swear words! Get it here: https://builtbybit.com/resources/18217/
# 2. Use our in-game rule creator in the "/chc rules" command.
# 3. Use our web-based rules generator at https://app.mineacademy.org/chatcontrol-rules-generator
# 4. Or learn about how to make rules at https://docs.mineacademy.org/chatcontrol/rules#making-efficient-rules