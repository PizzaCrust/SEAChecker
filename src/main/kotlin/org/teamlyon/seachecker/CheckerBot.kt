package org.teamlyon.seachecker

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.record.Country
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.entities.Member
import net.dv8tion.jda.core.entities.Role
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import online.pizzacrust.jettydsl.ServerResponse
import online.pizzacrust.jettydsl.server
import java.net.InetAddress
import java.util.*
import javax.servlet.http.HttpServletResponse.SC_OK

val geoIpDb = DatabaseReader.Builder(IntroHandler::class.java.classLoader
        .getResourceAsStream("countrydb.mmdb")).build()

val seaCountries = arrayOf("SG", "VN", "ID", "KH", "MY", "LA", "TH", "BR", "TL", "PH", "MM",
        "IN", "PH", "HK")

fun isSea(country: Country): Boolean {
    for (seaCountry in seaCountries) {
        if (country.isoCode.startsWith(seaCountry))
            return true
    }
    return false
}

val ignoreList: MutableList<Member> = mutableListOf()

// TBA
class IntroHandler: ListenerAdapter() {
    fun intro(member: Member) {
        member.user.openPrivateChannel().complete().sendMessage("To access some server channels, " +
                "simply visit the page to verify. https://waypointchecker.herokuapp" +
                ".com/verify?user=${member.user.idLong}&guild=${member.guild.idLong}/").queue()
    }
    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        intro(event.member)
        ignoreList.add(event.member)
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.message.contentStripped.startsWith("!verifywaypoint")) {
            intro(event.member)
            ignoreList.add(event.member)
        }
    }
}

fun giveRole(name: String, member: Member) {
    val role: Role = if (member.guild.getRolesByName(name, true).size == 0) {
        member.guild.controller.createRole().setName(name).complete()
    } else {
        member.guild.getRolesByName(name, true)[0]
    }
    member.guild.controller.addRolesToMember(member, role).queue()
}

fun main(vararg args: String) {
    val port = args[0].toInt()
    val jda = JDABuilder(args[1]).addEventListener(IntroHandler()).build()
    server(port) {
        path("/verify") { it, req ->
            // sends userid and guildlong
            val id = it.get("user")!![0].toLong()
            val guild = it.get("guild")!![0].replace("/","").toLong()
            val guildObj = jda.getGuildById(guild)
            val memberObj = guildObj.getMemberById(id)
            if (ignoreList.contains(memberObj)) {
                ignoreList.remove(memberObj)
                return@path ServerResponse(SC_OK, "Refresh the page to verify.")
            }
            val split = req.getHeader("X-Forwarded-For").split(",")
            val response = geoIpDb.country(InetAddress.getByName(split[split.size - 1]))
            giveRole(Locale("en", response.country.isoCode).isO3Country, memberObj)
            if (isSea(response.country)) {
                giveRole("SEA", memberObj)
            }
            memberObj.user.openPrivateChannel().complete().sendMessage("You have been verified " +
                    "in, **${guildObj.name}**.").queue()
            ServerResponse(SC_OK, "You have been verified " +
                    "in, ${guildObj.name}.")
        }
    }
}