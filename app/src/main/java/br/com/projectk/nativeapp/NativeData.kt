package br.com.projectk.nativeapp

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Modelos e parsers das páginas do site para as telas 100% nativas. */

data class LedgerEntry(
    val label: String,
    val whenText: String,
    val kcoin: String,
    val crystals: String,
)

data class WalletInfo(
    val kcoin: String,
    val crystals: String,
    val buyRate: String,
    val sellRate: String,
    val history: List<LedgerEntry>,
)

data class TaskInfo(
    val key: String,
    val title: String,
    val desc: String,
    val progress: String?,
    val reward: String,
    val state: String, // done | ready | pending
)

data class BadgeInfo(
    val key: String,
    val name: String,
    val desc: String,
    val rarity: String,
    val art: String?,
    val locked: Boolean,
    val special: Boolean,
)

data class ShopItem(
    val name: String,
    val desc: String,
    val price: String,
    val action: String,
    val kind: String,
    val item: String,
    val button: String,
    val previewText: String?,
    val previewImage: String?,
)

data class ShopTab(val label: String, val href: String, val active: Boolean)

data class GameInfo(val name: String, val desc: String, val href: String)

data class AdminStat(val value: String, val label: String, val alert: Boolean)

data class AdminRow(
    val title: String,
    val subtitle: String,
    val avatar: String?,
    val actions: List<Pair<String, String>>, // action path -> label
)

data class ProfileBadge(val name: String, val desc: String, val rarity: String, val art: String?)

object NativeData {
    fun doc(html: String): Document = Jsoup.parse(html)

    private fun styleHas(e: Element, needle: String): Boolean =
        e.attr("style").replace(" ", "").contains(needle.replace(" ", ""))

    private fun firstText(parent: Element, selector: String, needle: String): String =
        parent.select(selector).firstOrNull { styleHas(it, needle) }?.text()?.trim().orEmpty()

    fun wallet(d: Document): WalletInfo {
        val cards = d.select("div.notifications-card")
        val kcoin = cards.getOrNull(0)?.let { firstText(it, "div", "font-size:26px") } ?: "0"
        val crystals = cards.getOrNull(1)?.let { firstText(it, "div", "font-size:26px") } ?: "0"

        var buy = "1000"
        var sell = "900"
        val trocar = d.select("section.notifications-card").firstOrNull {
            it.text().contains("Trocar")
        }
        if (trocar != null) {
            val t = trocar.text()
            Regex("Compra:.*?=\\s*([0-9.,]+)").find(t)?.groupValues?.get(1)?.let { buy = it }
            Regex("Venda:.*?=\\s*([0-9.,]+)").find(t)?.groupValues?.get(1)?.let { sell = it }
        }

        val history = ArrayList<LedgerEntry>()
        for (row in d.select("div.notification-item")) {
            val left = row.select("div").firstOrNull() ?: continue
            val label = left.select("div").firstOrNull()?.text()?.trim().orEmpty()
            val whenText = left.select("time").firstOrNull()?.text()?.trim().orEmpty()
            if (label.isBlank()) continue
            val right = row.select("div").getOrNull(1)
            var kc = ""
            var cr = ""
            if (right != null) {
                for (delta in right.select("div")) {
                    val v = delta.text().trim()
                    if (v.contains("kcoin", true)) kc = v
                    else if (v.contains("cristal", true)) cr = v
                }
            }
            history.add(LedgerEntry(label, whenText, kc, cr))
        }
        return WalletInfo(kcoin, crystals, buy, sell, history)
    }

    fun tasks(d: Document): List<Pair<String, List<TaskInfo>>> {
        val sections = d.select("section.notifications-card")
        val out = ArrayList<Pair<String, List<TaskInfo>>>()
        val labels = listOf("MISSÕES DE HOJE", "CONQUISTAS ÚNICAS")
        sections.forEachIndexed { idx, section ->
            val items = ArrayList<TaskInfo>()
            for (form in section.select("form.notification-item")) {
                val key = form.select("input[name=task]").attr("value")
                if (key.isBlank()) continue
                val title = firstText(form, "div", "font-weight:700")
                val desc = form.select("div").firstOrNull {
                    styleHas(it, "opacity:.6") && !styleHas(it, "font-weight:700")
                }?.text()?.trim().orEmpty()
                val reward = firstText(form, "span", "font-weight:800")
                val progress = form.select("div").firstOrNull { styleHas(it, "linear-gradient") }
                    ?.attr("style")?.let { Regex("width:([0-9]+)%").find(it)?.groupValues?.get(1) }
                val button = form.select("button").firstOrNull()
                val state = when {
                    form.text().contains("✓ feito") -> "done"
                    button?.text()?.contains("Pegar") == true -> "ready"
                    else -> "pending"
                }
                items.add(TaskInfo(key, title, desc, progress, reward, state))
            }
            if (items.isNotEmpty()) {
                out.add((labels.getOrNull(idx) ?: "TAREFAS") to items)
            }
        }
        return out
    }

    fun badges(d: Document): Pair<String, List<BadgeInfo>> {
        val counter = d.select("div.page-heading span").last()?.text()?.trim().orEmpty()
        val list = ArrayList<BadgeInfo>()
        for (card in d.select("div.bd-card")) {
            val btn = card.select("button[data-bn]").firstOrNull()
            val name = btn?.attr("data-bn") ?: card.select("b").firstOrNull()?.text().orEmpty()
            val desc = btn?.attr("data-bd") ?: card.select("small").firstOrNull()?.text().orEmpty()
            val rarity = btn?.attr("data-br").orEmpty()
            val img = btn?.select("img")?.firstOrNull()
            val art = img?.absUrl("src")?.takeIf { it.isNotBlank() }
            list.add(
                BadgeInfo(
                    key = card.className().let { Regex("bd-card--([a-z]+)").find(it)?.groupValues?.get(1) ?: name.lowercase() },
                    name = name,
                    desc = desc,
                    rarity = rarity,
                    art = art,
                    locked = card.hasClass("lock"),
                    special = card.hasClass("bd-card--mister"),
                )
            )
        }
        return counter to list
    }

    fun shop(d: Document): Pair<List<ShopTab>, List<ShopItem>> {
        val tabs = d.select("nav.sp-tabs a").map {
            ShopTab(it.text().trim(), it.attr("href"), it.hasClass("on"))
        }
        val items = ArrayList<ShopItem>()
        for (card in d.select("section.sp-card")) {
            val name = firstText(card, "div", "font-weight:800")
            val desc = card.select("div").firstOrNull {
                styleHas(it, "opacity:.6") && !styleHas(it, "font-weight:800")
            }?.text()?.trim().orEmpty()
            val price = card.select("span.sp-price").firstOrNull()?.text()?.trim().orEmpty()
            val form = card.select("form").firstOrNull()
            val action = form?.attr("action").orEmpty()
            val kind = form?.select("input[name=kind]")?.attr("value").orEmpty()
            val item = form?.select("input[name=item]")?.attr("value").orEmpty()
            val button = form?.select("button")?.firstOrNull()?.text()?.trim().orEmpty()
            val stage = card.select("div.sp-stage").firstOrNull()
            val previewImage = stage?.select("img")?.firstOrNull()?.absUrl("src")?.takeIf { it.isNotBlank() }
            val previewText = stage?.select("div,button")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
            items.add(ShopItem(name, desc, price, action, kind, item, button, previewText, previewImage))
        }
        return tabs to items
    }

    fun games(d: Document): List<GameInfo> = d.select("div.jg-grid a").mapNotNull { a ->
        val small = a.select("small").firstOrNull()?.text()?.trim().orEmpty()
        val name = a.text().removeSuffix(small).trim()
        if (name.isBlank()) null else GameInfo(name, small, a.absUrl("href"))
    }

    fun admin(d: Document): Triple<List<String>, List<AdminStat>, List<AdminRow>> {
        val tabs = d.select("nav.k-tabs a").map { it.text().trim() }.filter { it.isNotBlank() }
        val stats = d.select("div.k-stat").map {
            AdminStat(
                it.select("strong").firstOrNull()?.text()?.trim().orEmpty(),
                it.select("span").firstOrNull()?.text()?.trim().orEmpty(),
                it.hasClass("k-stat--alert"),
            )
        }
        val rows = ArrayList<AdminRow>()
        for (row in d.select("div.k-row")) {
            val main = row.select("div.k-row__main").firstOrNull()
            val title = main?.select("strong")?.firstOrNull()?.text()?.trim()
                ?: main?.select("a")?.firstOrNull()?.text()?.trim()
                ?: row.select("strong").firstOrNull()?.text()?.trim()
                ?: continue
            val subtitle = main?.text()?.trim()?.removePrefix(title)?.trim().orEmpty()
            val avatar = row.select("img").firstOrNull()?.absUrl("src")?.takeIf { it.isNotBlank() }
            val actions = row.select("form").mapNotNull { form ->
                val label = form.select("button").firstOrNull()?.text()?.trim()
                val act = form.attr("action")
                if (label.isNullOrBlank() || act.isBlank()) null else act to label
            }
            rows.add(AdminRow(title, subtitle, avatar, actions))
        }
        return Triple(tabs, stats, rows)
    }

    fun profileHeader(d: Document): ProfileBadge? {
        val b = d.select("button[data-bn]").firstOrNull() ?: return null
        return ProfileBadge(
            name = b.attr("data-bn"),
            desc = b.attr("data-bd"),
            rarity = b.attr("data-br"),
            art = b.select("img").firstOrNull()?.absUrl("src")?.takeIf { it.isNotBlank() },
        )
    }

    fun profileExtras(d: Document): ProfileExtras {
        val coverStyle = d.select("section.z-profile-cover").firstOrNull()?.attr("style").orEmpty()
        val cover = Regex("url\\(['\"]?(.+?)['\"]?\\)").find(coverStyle)?.groupValues?.get(1)
        val avatar = d.select("img.z-profile-avatar").firstOrNull()?.absUrl("src")?.takeIf { it.isNotBlank() }
        val wrap = d.select("div.z-profile-avatar-wrap").firstOrNull()
        val frame = wrap?.classNames()?.firstOrNull { it != "z-profile-avatar-wrap" && it.isNotBlank() }
        val honor = d.select("button.k-honor-t").firstOrNull()
        val honorRaw = honor?.attr("data-k-title").orEmpty()
        var honorName: String? = null
        var honorArt: String? = null
        if (honorRaw.isNotBlank()) {
            try {
                val o = org.json.JSONObject(honorRaw)
                honorName = o.optString("name").takeIf { it.isNotBlank() }
                honorArt = o.optString("image").takeIf { it.isNotBlank() }
            } catch (_: Throwable) {
                honorName = honor?.select("span.k-honor-name")?.firstOrNull()?.text()?.trim()
                honorArt = honor?.select("img.k-honor-art")?.firstOrNull()?.absUrl("src")
            }
        }
        val status = d.select("div.pst-b").firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
        val badges = d.select("button[data-bn]").mapNotNull { b ->
            val n = b.attr("data-bn")
            if (n.isBlank()) null else ProfileBadge(
                n,
                b.attr("data-bd"),
                b.attr("data-br"),
                b.select("img").firstOrNull()?.absUrl("src")?.takeIf { it.isNotBlank() },
            )
        }
        return ProfileExtras(cover, avatar, frame, honorName, honorArt, honorRaw, status, badges)
    }

    data class ProfileExtras(
        val cover: String?,
        val avatar: String?,
        val frame: String?,
        val honorName: String?,
        val honorArt: String?,
        val honorJson: String,
        val status: String?,
        val badges: List<ProfileBadge>,
    )
}
