package netfix.content

import netfix.App
import netfix.helpers.Prefs.CUB
import netfix.helpers.Prefs.FAV
import netfix.helpers.Prefs.contToRemove
import netfix.helpers.Prefs.syncEnabled
import netfix.models.NetfixCard

class Continued : NetfixProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Continued.get())
    }

    companion object {
        fun get(): List<NetfixCard> {
            val lst = mutableListOf<NetfixCard>()
            // CUB
            if (App.context.syncEnabled) {
                App.context.CUB
                    ?.filter { it.type == NetfixProvider.CONT }
                    ?.reversed()
                    ?.mapNotNull { it.data?.apply { fixCard() } }
                    ?.let { lst.addAll(it) }
            }
            // FAV
            App.context.FAV?.card
                ?.filter { App.context.FAV?.continued?.contains(it.id.toString()) == true }
                ?.let { lst.addAll(it) }
            // Exclude pending and reverse the final list
            return lst
                .filterNot { App.context.contToRemove.contains(it.id.toString()) }
                .reversed()
        }
    }
}