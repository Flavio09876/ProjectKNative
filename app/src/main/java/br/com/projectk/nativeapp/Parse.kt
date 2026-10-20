package br.com.projectk.nativeapp

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class Post(
    val id: Int, val name: String, val username: String, val avatar: String?, val time: String,
    val text: String, val media: String?, var liked: Boolean, var likes: Int, val comments: String,
    val verified: Boolean = false, val role: String = "", val frame: String? = null
)

data class Comment(
    val id: Int, val name: String, val username: String, val avatar: String?, val meta: String,
    val text: String, val depth: Boolean, val verified: Boolean, val frame: String?
)

data class Notif(val text: String, val href: String, val avatar: String?)
data class Server(val href: String, val name: String, val members: String, val desc: String, val icon: String?)
data class HomeMeta(val me: String?, val avatar: String?, val unread: Boolean, val isAdmin: Boolean)

data class Person(
    val name: String, val username: String, val avatar: String?, val bio: String, var following: Boolean, val showFollow: Boolean
)

data class Room(val id: Int, val name: String, val members: String, val desc: String, val icon: String?)
data class Community(val id: Int, val name: String)
data class Convo(val username: String, val name: String, val avatar: String?, val preview: String, val time: String, val unread: Int)
data class Profile(
    val name: String, val username: String, val bio: String, val avatar: String?,
    val stats: List<Pair<String, String>>, val posts: List<Post>, val following: Boolean, val isMe: Boolean,
    val cover: String? = null, val status: String = "", val badges: List<String> = emptyList(),
    val verified: Boolean = false, val frame: String? = null, val role: String = ""
)

object Parse {
    @Volatile var avatarHtml: String? = null
    @Volatile var cssLinks: List<String> = emptyList()
    @Volatile var styles: String = ""

    fun doc(html: String): Document = Jsoup.parse(html, Net.BASE).also { Frames.feed(it); Frames.addCss(it) }

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
                    comments,
                    a.selectFirst(".k-post__name .k-verified") != null,
                    if (a.selectFirst(".k-rtag--admin") != null) "admin" else if (a.selectFirst(".k-rtag--mod") != null) "mod" else "",
                    frameKey(a.selectFirst(".k-post__author"))
                )
            )
        }
        return out
    }


    private val SIZES = setOf("xs", "sm", "md", "lg", "xl")

    /** Classes CSS da moldura (k-frame ...), sem as de tamanho. Null se não houver moldura. */
    fun frameKey(root: Element?): String? {
        if (root == null) return null
        val els = ArrayList<Element>()
        els.add(root)
        els.addAll(root.select(".k-frame"))
        for (e in els) {
            val cn = e.classNames()
            if (!cn.contains("k-frame")) continue
            val keep = cn.filter { !(it.startsWith("k-frame--") && it.removePrefix("k-frame--") in SIZES) && !it.startsWith("z-") && !it.startsWith("k-avatar") }
            if (keep.any { it.startsWith("k-frame--") }) return keep.joinToString(" ")
        }
        return null
    }

    fun comments(d: Document): List<Comment> {
        val out = ArrayList<Comment>()
        for (a in d.select("article.k-comment")) {
            val id = a.id().removePrefix("c").toIntOrNull() ?: continue
            val nameEl = a.selectFirst(".k-comment__name")
            val meta = a.selectFirst(".k-comment__meta")?.text().orEmpty()
            out.add(
                Comment(
                    id,
                    nameEl?.selectFirst("span")?.ownText()?.trim().orEmpty().ifBlank { nameEl?.text().orEmpty() },
                    meta.substringBefore(" ").removePrefix("@"),
                    src(a.selectFirst("img")),
                    meta,
                    a.selectFirst(".k-comment__text")?.text().orEmpty(),
                    a.attr("style").contains("margin-left"),
                    a.selectFirst(".k-verified") != null,
                    frameKey(a)
                )
            )
        }
        return out
    }

    fun notifs(d: Document): List<Notif> {
        val out = ArrayList<Notif>()
        for (e in d.select("section.notifications-card:not(.k-updates) .notification-item")) {
            val href = if (e.tagName() == "a") e.attr("href") else e.selectFirst("a[href]")?.attr("href").orEmpty()
            val t = e.text().trim()
            if (t.isNotBlank()) out.add(Notif(t, href, src(e.selectFirst("img"))))
        }
        return out
    }

    fun servers(d: Document): List<Server> {
        val out = ArrayList<Server>()
        for (a in d.select("a.sv-card")) {
            val smalls = a.select("small")
            out.add(
                Server(
                    a.attr("href"), a.selectFirst("b")?.text().orEmpty(),
                    smalls.firstOrNull()?.text().orEmpty(), if (smalls.size > 1) smalls[1].text() else "",
                    src(a.selectFirst("img.sv-ic"))
                )
            )
        }
        return out
    }

    fun homeRooms(d: Document): List<Room> {
        val out = ArrayList<Room>()
        for (a in d.select("a.hr-card")) {
            val id = a.attr("href").trimEnd('/').substringAfterLast("/").toIntOrNull() ?: continue
            out.add(Room(id, a.selectFirst("b")?.text().orEmpty(), a.selectFirst("small")?.text().orEmpty(), "", src(a.selectFirst("img.hr-ic"))))
        }
        return out
    }

    fun homeMeta(d: Document): HomeMeta {
        val me = meUsername(d)
        var av: String? = null
        if (me != null) {
            av = src(d.selectFirst("a[href='/u/$me'] img"))
        }
        return HomeMeta(me, av, d.selectFirst(".notification-dot") != null, d.selectFirst("a[href='/admin']") != null)
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
        val coverStyle = d.selectFirst(".z-profile-cover")?.attr("style").orEmpty()
        val cover = Regex("""url\(['"]?([^'")]+)['"]?\)""").find(coverStyle)?.groupValues?.get(1)
        avatarHtml = d.selectFirst(".z-profile-avatar-wrap")?.outerHtml()
        cssLinks = d.select("link[rel=stylesheet]").map { it.absUrl("href") }.filter { it.isNotBlank() }
        styles = d.select("style").joinToString("") { it.data() }
        val badges = ArrayList<String>()
        for (b in d.select(".k-badgebar .kbtn svg")) {
            var x = b.outerHtml()
            try { x = org.jsoup.Jsoup.parse(x, "", org.jsoup.parser.Parser.xmlParser()).outerHtml() } catch (e: Exception) {}
            x = x.replace(Regex("^<\\?xml[^>]*>\\s*"), "")
            if (!x.contains("xmlns")) x = x.replaceFirst("<svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"")
            badges.add(x)
        }
        return Profile(
            name, username,
            d.selectFirst(".z-profile-bio")?.text().orEmpty(),
            src(d.selectFirst(".z-profile-avatar-wrap img")),
            stats, posts(d), btn?.attr("data-following") == "true", isMe,
            Net.abs(cover),
            d.selectFirst(".pst-b span")?.text().orEmpty(),
            badges,
            d.selectFirst(".z-profile-name-row .z-verified") != null,
            frameKey(d.selectFirst(".z-profile-avatar-wrap")),
            if (d.selectFirst(".z-profile-name-row .k-rtag--admin") != null) "admin" else if (d.selectFirst(".z-profile-name-row .k-rtag--mod") != null) "mod" else ""
        )
    }

    fun meUsername(d: Document): String? {
        val a = d.selectFirst("a[href^='/u/']") ?: return null
        val u = userFromHref(a.attr("href"))
        return if (u.isBlank()) null else u
    }

    /** Campos atuais de um formulário (para reenviar tudo igual, mudando só o que o usuário editou). */
    fun formFields(d: Document, actionPart: String): List<Pair<String, String>> {
        val f = (d.selectFirst("form[action*='$actionPart']") ?: d.selectFirst("textarea[name=bio]")?.closest("form") ?: d.select("form[method~=(?i)post]").firstOrNull { it.selectFirst("[name=name]") != null }) ?: return emptyList()
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
