package top.netfix.content

import top.netfix.App
import top.netfix.helpers.Prefs.CUB
import top.netfix.helpers.Prefs.FAV
import top.netfix.helpers.Prefs.syncEnabled
import top.netfix.helpers.Prefs.viewToRemove
import top.netfix.models.LampaCard

class Viewed : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Viewed.get())
    }

    companion object {
        fun get(): List<LampaCard> {
            val lst = mutableListOf<LampaCard>()
            // CUB
            if (App.context.syncEnabled) {
                App.context.CUB
                    ?.filter { it.type == LampaProvider.VIEW }
                    ?.reversed()
                    ?.mapNotNull { it.data?.apply { fixCard() } }
                    ?.let { lst.addAll(it) }
            }
            // FAV
            App.context.FAV?.card
                ?.filter { App.context.FAV?.viewed?.contains(it.id.toString()) == true }
                ?.let { lst.addAll(it) }
            // Exclude pending and reverse the final list
            return lst
                .filterNot { App.context.viewToRemove.contains(it.id.toString()) }
                .reversed()
        }
    }
}