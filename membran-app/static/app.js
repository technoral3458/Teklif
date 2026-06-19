// Ortak yardımcılar: modal yönetimi, form gönderimi, tarama.

function openModal(id){ document.getElementById(id).classList.remove('hidden'); }
function closeModal(id){ document.getElementById(id).classList.add('hidden'); }

// ESC ile kapat
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') document.querySelectorAll('.modal-bg').forEach(m => m.classList.add('hidden'));
});
// Arka plana tıklayınca kapat
document.addEventListener('click', (e) => {
  if (e.target.classList && e.target.classList.contains('modal-bg')) e.target.classList.add('hidden');
});

async function postForm(url, data){
  const fd = new FormData();
  for (const k in data) fd.append(k, data[k] == null ? '' : data[k]);
  return fetch(url, { method: 'POST', body: fd });
}

// El yazısı tarama (list_detail sayfasında kullanılır)
async function doScan(lid){
  const fileInput = document.getElementById('scanFile');
  const result = document.getElementById('scanResult');
  const btn = document.getElementById('scanBtn');
  if (!fileInput.files.length){ alert('Önce bir görsel seçin.'); return; }
  btn.disabled = true; btn.textContent = 'Taranıyor...';
  result.innerHTML = '<div class="text-slate-500">Claude görseli okuyor...</div>';
  try{
    const fd = new FormData();
    fd.append('image', fileInput.files[0]);
    const r = await fetch(`/membrane/list/${lid}/door/scan`, { method:'POST', body: fd });
    const d = await r.json();
    if (d.error){ result.innerHTML = `<div class="text-red-600">${d.error}</div>`; }
    else {
      let html = `<div class="text-emerald-700 mb-1">${d.added} kapı eklendi:</div><table class="w-full">`;
      d.doors.forEach(x => html += `<tr class="border-b"><td>${x.door_name||''}</td><td>${x.width_mm}×${x.height_mm}</td><td>${x.quantity} adet</td></tr>`);
      html += '</table><div class="text-xs text-slate-400 mt-2">Sayfayı yenileyin.</div>';
      result.innerHTML = html;
      setTimeout(() => location.reload(), 1500);
    }
  } catch(err){ result.innerHTML = `<div class="text-red-600">Hata: ${err}</div>`; }
  finally{ btn.disabled = false; btn.textContent = 'Tara'; }
}
