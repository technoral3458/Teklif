package com.teklif.yolcu.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.teklif.yolcu.nav.Nav
import com.teklif.yolcu.nav.Place
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Araç ekranında hedef arama; seçilince rota hesaplanır ve navigasyon hemen başlar. */
class CarSearchScreen(carContext: CarContext) : Screen(carContext) {

    private var results: List<Place> = emptyList()
    private var loading = false
    private var job: Job? = null

    private val callback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) = search(searchText, delayMs = 700)
        override fun onSearchSubmitted(searchText: String) = search(searchText, delayMs = 0)
    }

    private fun search(text: String, delayMs: Long) {
        job?.cancel()
        if (text.length < 3) {
            results = emptyList(); loading = false; invalidate(); return
        }
        loading = true
        invalidate()
        job = lifecycleScope.launch {
            delay(delayMs)
            results = Nav.search(text).take(6)
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder().setNoItemsMessage("Aramak için yazın")
        results.forEach { p ->
            list.addItem(
                Row.Builder()
                    .setTitle(p.name)
                    .addText(p.detail.ifBlank { " " })
                    .setOnClickListener {
                        Nav.planRoute(p, autoStart = true)
                        screenManager.pop()
                    }
                    .build()
            )
        }
        return SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("Nereye?")
            .setShowKeyboardByDefault(true)
            .setLoading(loading)
            .setItemList(list.build())
            .build()
    }
}
