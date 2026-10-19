package br.com.projectk.nativeapp

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class Post(
    val id: Int, val name: String, val username: String, val avatar: String?, val time: String,
    val text: String, val media: String?, var liked: Boolean, var likes: Int, val comments: String
)

data class Person(
    val name: String, val username: String, val avatar: String?, val bio: String, var following: Boolean, val showFollow: Boolean
)

data class Room(val id: Int, val name: String, val members: String, val desc: String, val icon: String?)
data class Community(val id: Int, val name: String)
data class Convo(val username: String, val name: String, val avatar: String?, val preview: String, val time: String, val unread: Int)
data class Profile(
    val name: String, val username: String, val bio: String, val avatar: String?,
    val stats: List<Pair<String, String>>, val posts: List<Post>, val following: Boolean, val isMe: Boolean
)

object Parse {
    fun doc(html: String): Document = Jsoup.parse(html, Net.BASE)

    private fun src(e: Element?): String? {
        if (e == null) return null
        val s = e.attr("src")
        return if (s.isBlank()) null else Net.abs(s)
    }

    private fun userFromHref(href: String): String =
        href.substringAfter("/u/").substringBefore("?").substringBefore("#").substringBefore("/")

    fun posts(d: Document): List<Post> {
        val out = ArrayList<Post>()
        for (a in d.select("article.k-post")) {
            val like = a.selectFirst(".k-like") ?: continue
            val id = like.attr("data-post-id").toIntOrNull() ?: continue
            val t = a.selectFirst(".k-post__text")
            var text = ""
            if (t != null) {
                t.select("br").append("\n")
                text = t.wholeText().trim()
            }
            val comments = a.selectFirst("a.k-action")?.select("span")?.last()?.text() ?: "0"
            out.add(
                Post(
                    id,
                    a.selectFirst(".k-post__nametext")?.text().orEmpty(),
                    a.selectFirst(".k-post__meta")?.text().orEmpty().removePrefix("@").trim(),
                    src(a.selectFirst(".k-post__author img")),
                    a.selectFirst(".k-post__time")?.text().orEmpty(),
                    text,
                    src(a.selectFirst("img.k-post__media")),
                    like.hasClass("liked"),
                    a.selectFirst(".like-count")?.text()?.trim()?.toIntOrNull() ?: 0,
                    comments
                )
            )
        }
        return out
    }

    fun moreHref(d: Document): String? {
        val h = d.selectFirst("a[href*='before=']")?.attr("href")
        return if (h.isNullOrBlank()) null else h
    }

    fun tags(d: Document): List<String> = d.select("a.k-tag").map { it.text().trim() }.filter { it.isNotBlank() }

    fun discoverPeople(d: Document): List<Person> {
        val out = ArrayList<Person>()
        for (a in d.select("article.user-card")) {
            val href = a.selectFirst("a.user-card-profile")?.attr("href") ?: continue
            val u = userFromHref(href)
            if (u.isBlank()) continue
            val btn = a.selectFirst("button.follow-button")
            out.add(
                Person(
                    a.selectFirst("h3")?.ownText()?.trim().orEmpty().ifBlank { u },
                    u,
                    src(a.selectFirst("img")),
                    a.selectFirst("p")?.text().orEmpty(),
                    btn?.attr("data-following") == "true",
                    btn != null
                )
            )
        }
        return out
    }

    fun searchPeople(d: Document): List<Person> {
        val out = ArrayList<Person>()
        for (a in d.select("a.notification-item[href^='/u/']")) {
            val u = userFromHref(a.attr("href"))
            if (u.isBlank()) continue
            out.add(Person(a.selectFirst("strong")?.text().orEmpty().ifBlank { u }, u, src(a.selectFirst("img")), "", false, false))
        }
        return out
    }

    fun rooms(d: Document): List<Room> {
        val out = ArrayList<Room>()
        for (a in d.select("a.rm-card")) {
            val id = a.attr("href").trimEnd('/').substringAfterLast("/").toIntOrNull() ?: continue
            val smalls = a.select("small")
            out.add(
                Room(
                    id,
                    a.selectFirst("b")?.text().orEmpty(),
                    smalls.firstOrNull()?.text().orEmpty(),
                    if (smalls.size > 1) smalls[1].text() else "",
                    src(a.selectFirst("img.rm-ic"))
                )
            )
        }
        return out
    }

    fun communities(d: Document): List<Community> {
        val out = ArrayList<Community>()
        for (a in d.select("a[href^='/community/']")) {
            val id = a.attr("href").trimEnd('/').substringAfterLast("/").toIntOrNull() ?: continue
            val name = a.selectFirst("h1,h2,h3,h4,strong,b")?.text() ?: a.text().take(40)
            out.add(Community(id, name.ifBlank { "Comunidade $id" }))
        }
        return out
    }

    fun convos(d: Document): List<Convo> {
        val out = ArrayList<Convo>()
        for (a in d.select("a.k-convo")) {
            val u = a.attr("href").trimEnd('/').substringAfterLast("/")
            if (u.isBlank()) continue
            out.add(
                Convo(
                    u,
                    a.selectFirst(".k-convo__name")?.text().orEmpty().ifBlank { u },
                    src(a.selectFirst("img")),
                    a.selectFirst(".k-convo__preview")?.text().orEmpty(),
                    a.selectFirst(".k-convo__time")?.text().orEmpty(),
                    a.selectFirst(".k-convo__badge")?.text()?.trim()?.toIntOrNull() ?: 0
                )
            )
        }
        return out
    }

    fun profile(d: Document, username: String, isMe: Boolean): Profile {
        val h1 = d.selectFirst(".z-profile-name-row h1")
        val name = (h1?.ownText()?.trim().orEmpty()).ifBlank { h1?.text().orEmpty() }.ifBlank { username }
        val stats = d.select(".z-stat").map { Pair(it.selectFirst("span")?.text().orEmpty(), it.selectFirst("strong")?.text().orEmpty()) }
        val btn = d.selectFirst("[data-following]")
        return Profile(
            name, username,
            d.selectFirst(".z-profile-bio")?.text().orEmpty(),
            src(d.selectFirst(".z-profile-avatar-wrap img")),
            stats, posts(d), btn?.attr("data-following") == "true", isMe
        )
    }

    fun meUsername(d: Document): String? {
        val a = d.selectFirst("a[href^='/u/']") ?: return null
        val u = userFromHref(a.attr("href"))
        return if (u.isBlank()) null else u
    }

    /** Campos atuais de um formulário (para reenviar tudo igual, mudando só o que o usuário editou). */
    fun formFields(d: Document, actionPart: String): List<Pair<String, String>> {
        val f = d.selectFirst("form[action*='$actionPart']") ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (i in f.select("input[name]")) {
            val type = i.attr("type").lowercase()
            if (type == "file" || type == "submit" || type == "button") continue
            if ((type == "radio" || type == "checkbox") && !i.hasAttr("checked")) continue
            out.add(Pair(i.attr("name"), i.attr("value")))
        }
        for (t in f.select("textarea[name]")) out.add(Pair(t.attr("name"), t.wholeText()))
        for (s in f.select("select[name]")) {
            val o = s.selectFirst("option[selected]") ?: s.selectFirst("option")
            if (o != null) out.add(Pair(s.attr("name"), o.attr("value")))
        }
        return out
    }

    fun flash(d: Document): String? = d.selectFirst(".flash-message span")?.text()
}
