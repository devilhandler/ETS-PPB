@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.libraryreg

import android.content.Context
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

// ---------- Warna ----------
val Biru = Color(0xFF1E5BD8)
val BiruMuda = Color(0xFFDCE6FB)
val Kuning = Color(0xFFE9A100)
val Merah = Color(0xFFC62828)

// ---------- Data ----------
data class Anggota(
    val id: Long, val nama: String, val nomorInduk: String, val prodi: String,
    val alamat: String, val telepon: String, val email: String, val jenis: String
)

val daftarProdi = listOf("Teknik Informatika", "Sistem Informasi", "Teknik Elektro", "Manajemen", "Unit Administrasi")
val daftarJenis = listOf("Mahasiswa", "Dosen", "Staf")

// Menyimpan data ke SharedPreferences (format JSON) agar tidak hilang saat aplikasi ditutup
class Penyimpanan(context: Context) {
    private val prefs = context.getSharedPreferences("libraryreg", Context.MODE_PRIVATE)

    fun muat(): List<Anggota> {
        val arr = JSONArray(prefs.getString("anggota", null) ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Anggota(
                o.getLong("id"), o.getString("nama"), o.getString("nomorInduk"), o.getString("prodi"),
                o.getString("alamat"), o.getString("telepon"), o.getString("email"), o.getString("jenis")
            )
        }
    }

    fun simpan(data: List<Anggota>) {
        val arr = JSONArray()
        data.forEach { a ->
            arr.put(
                JSONObject().put("id", a.id).put("nama", a.nama).put("nomorInduk", a.nomorInduk)
                    .put("prodi", a.prodi).put("alamat", a.alamat).put("telepon", a.telepon)
                    .put("email", a.email).put("jenis", a.jenis)
            )
        }
        prefs.edit().putString("anggota", arr.toString()).apply()
    }
}

// ---------- Validasi ----------
fun validasi(a: Anggota, semua: List<Anggota>): Map<String, String> {
    val e = mutableMapOf<String, String>()
    if (a.nama.isBlank()) e["nama"] = "Nama wajib diisi"
    if (!a.nomorInduk.matches(Regex("\\d{8,18}"))) e["nomorInduk"] = "Nomor induk harus 8–18 digit angka"
    else if (semua.any { it.nomorInduk == a.nomorInduk && it.id != a.id }) e["nomorInduk"] = "Nomor induk sudah terdaftar"
    if (a.prodi.isBlank()) e["prodi"] = "Program studi wajib dipilih"
    if (a.alamat.isBlank()) e["alamat"] = "Alamat wajib diisi"
    if (!a.telepon.matches(Regex("08\\d{8,11}"))) e["telepon"] = "Nomor telepon diawali 08, 10–13 digit"
    if (!Patterns.EMAIL_ADDRESS.matcher(a.email).matches()) e["email"] = "Format email tidak valid"
    if (a.jenis.isBlank()) e["jenis"] = "Jenis keanggotaan wajib dipilih"
    return e
}

// ---------- Navigasi ----------
sealed interface Layar {
    data object Beranda : Layar
    data object Daftar : Layar
    data class Form(val id: Long?) : Layar // id null = tambah, ada id = edit
    data class Detail(val id: Long) : Layar
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val penyimpanan = Penyimpanan(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Biru, primaryContainer = BiruMuda)) {
                LibraryRegApp(penyimpanan)
            }
        }
    }
}

@Composable
fun LibraryRegApp(penyimpanan: Penyimpanan) {
    val anggota = remember { penyimpanan.muat().toMutableStateList() }
    val stack = remember { mutableStateListOf<Layar>(Layar.Beranda) }
    val layar = stack.last()
    var hapus by remember { mutableStateOf<Anggota?>(null) }
    var pesan by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    fun buka(l: Layar) { stack.add(l) }
    fun kembali() { stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.size > 1) { kembali() }
    LaunchedEffect(pesan) { pesan?.let { snackbar.showSnackbar(it); pesan = null } }

    Scaffold(
        topBar = {
            if (layar != Layar.Beranda) TopAppBar(
                title = { Text(judul(layar)) },
                navigationIcon = {
                    IconButton(onClick = { kembali() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Biru, titleContentColor = Color.White, navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            key(layar) {
                when (layar) {
                    Layar.Beranda -> Beranda(
                        total = anggota.size,
                        onDaftar = { buka(Layar.Form(null)) },
                        onLihat = { buka(Layar.Daftar) }
                    )
                    Layar.Daftar -> DaftarAnggota(
                        semua = anggota,
                        onTambah = { buka(Layar.Form(null)) },
                        onLihat = { buka(Layar.Detail(it.id)) },
                        onEdit = { buka(Layar.Form(it.id)) },
                        onHapus = { hapus = it }
                    )
                    is Layar.Form -> FormAnggota(
                        awal = anggota.find { it.id == layar.id },
                        semua = anggota,
                        onSimpan = { a ->
                            val i = anggota.indexOfFirst { it.id == a.id }
                            if (i >= 0) anggota[i] = a else anggota.add(a)
                            penyimpanan.simpan(anggota)
                            pesan = if (i >= 0) "Data anggota berhasil diperbarui" else "Anggota berhasil didaftarkan"
                            stack.clear(); stack.add(Layar.Beranda); stack.add(Layar.Daftar)
                        }
                    )
                    is Layar.Detail -> anggota.find { it.id == layar.id }?.let { a ->
                        DetailAnggota(a, onEdit = { buka(Layar.Form(a.id)) }, onHapus = { hapus = a })
                    }
                }
            }
        }
    }

    // Dialog konfirmasi hapus
    hapus?.let { a ->
        AlertDialog(
            onDismissRequest = { hapus = null },
            title = { Text("Hapus anggota?") },
            text = { Text("Data ${a.nama} (${a.nomorInduk}) akan dihapus permanen dan tidak bisa dikembalikan.") },
            confirmButton = {
                Button(
                    onClick = {
                        anggota.remove(a)
                        penyimpanan.simpan(anggota)
                        hapus = null
                        if (layar is Layar.Detail) kembali()
                        pesan = "Anggota berhasil dihapus"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Merah)
                ) { Text("Hapus") }
            },
            dismissButton = { OutlinedButton(onClick = { hapus = null }) { Text("Batal") } }
        )
    }
}

fun judul(l: Layar) = when (l) {
    Layar.Beranda -> "LibraryReg"
    Layar.Daftar -> "Daftar Anggota"
    is Layar.Form -> if (l.id == null) "Formulir Pendaftaran" else "Edit Anggota"
    is Layar.Detail -> "Detail Anggota"
}

// ---------- Layar 1: Beranda ----------
@Composable
fun Beranda(total: Int, onDaftar: () -> Unit, onLihat: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.size(64.dp).background(Biru, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Text("📖", fontSize = 30.sp)
        }
        Text("LibraryReg", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("Pendaftaran Anggota Perpustakaan", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        MenuCard("Pendaftaran Anggota", "Isi formulir anggota baru", Icons.Default.Person, onDaftar)
        MenuCard("Daftar Anggota", "Lihat, cari, edit, dan hapus data", Icons.Default.Search, onLihat)
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Biru)
                Text("Total anggota", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun MenuCard(judul: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(BiruMuda, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Biru)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(judul, fontWeight = FontWeight.SemiBold)
                Text(sub, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

// ---------- Layar 2: Formulir (tambah & edit) ----------
@Composable
fun FormAnggota(awal: Anggota?, semua: List<Anggota>, onSimpan: (Anggota) -> Unit) {
    var nama by rememberSaveable { mutableStateOf(awal?.nama ?: "") }
    var nim by rememberSaveable { mutableStateOf(awal?.nomorInduk ?: "") }
    var prodi by rememberSaveable { mutableStateOf(awal?.prodi ?: "") }
    var alamat by rememberSaveable { mutableStateOf(awal?.alamat ?: "") }
    var telp by rememberSaveable { mutableStateOf(awal?.telepon ?: "") }
    var email by rememberSaveable { mutableStateOf(awal?.email ?: "") }
    var jenis by rememberSaveable { mutableStateOf(awal?.jenis ?: "") }
    var error by remember { mutableStateOf(mapOf<String, String>()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Input("Nama Lengkap", nama, error["nama"]) { nama = it }
        Input("NIM / NIP", nim, error["nomorInduk"], KeyboardType.Number) { nim = it }
        Pilihan("Program Studi / Unit", prodi, daftarProdi, error["prodi"]) { prodi = it }
        Input("Alamat", alamat, error["alamat"]) { alamat = it }
        Input("Nomor Telepon", telp, error["telepon"], KeyboardType.Phone) { telp = it }
        Input("Email", email, error["email"], KeyboardType.Email) { email = it }
        Pilihan("Jenis Keanggotaan", jenis, daftarJenis, error["jenis"]) { jenis = it }
        Button(
            onClick = {
                val a = Anggota(
                    awal?.id ?: System.currentTimeMillis(), nama.trim(), nim.trim(), prodi,
                    alamat.trim(), telp.trim(), email.trim(), jenis
                )
                error = validasi(a, semua)
                if (error.isEmpty()) onSimpan(a)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp)
        ) { Text(if (awal == null) "Daftar" else "Simpan Perubahan") }
    }
}

@Composable
fun Input(label: String, nilai: String, error: String?, tipe: KeyboardType = KeyboardType.Text, onUbah: (String) -> Unit) {
    OutlinedTextField(
        value = nilai, onValueChange = onUbah, modifier = Modifier.fillMaxWidth(),
        label = { Text("$label *") }, singleLine = true, isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = tipe)
    )
}

@Composable
fun Pilihan(label: String, nilai: String, opsi: List<String>, error: String?, onPilih: (String) -> Unit) {
    var buka by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = nilai, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("$label *") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            isError = error != null, supportingText = if (error != null) { { Text(error) } } else null
        )
        Box(Modifier.matchParentSize().clickable { buka = true }) // area klik untuk membuka pilihan
        DropdownMenu(expanded = buka, onDismissRequest = { buka = false }) {
            opsi.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onPilih(it); buka = false }) }
        }
    }
}

// ---------- Layar 3: Daftar Anggota ----------
@Composable
fun DaftarAnggota(
    semua: List<Anggota>, onTambah: () -> Unit,
    onLihat: (Anggota) -> Unit, onEdit: (Anggota) -> Unit, onHapus: (Anggota) -> Unit
) {
    var cari by rememberSaveable { mutableStateOf("") }
    val hasil = semua.filter { it.nama.contains(cari, ignoreCase = true) || it.nomorInduk.contains(cari) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = cari, onValueChange = { cari = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari nama atau nomor induk") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, shape = RoundedCornerShape(24.dp)
            )
            Text("${hasil.size} anggota", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 10.dp))
            if (hasil.isEmpty()) Text("Belum ada data anggota.", color = Color.Gray)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(hasil, key = { it.id }) { a ->
                    Card(onClick = { onLihat(a) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Inisial(a.nama, 40.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(a.nama, fontWeight = FontWeight.SemiBold)
                                Text("${a.nomorInduk} · ${a.jenis}", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { onLihat(a) }) { Icon(Icons.Default.Info, "Lihat", tint = Biru) }
                            IconButton(onClick = { onEdit(a) }) { Icon(Icons.Default.Edit, "Edit", tint = Kuning) }
                            IconButton(onClick = { onHapus(a) }) { Icon(Icons.Default.Delete, "Hapus", tint = Merah) }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onTambah, containerColor = Biru,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Default.Add, "Tambah", tint = Color.White) }
    }
}

@Composable
fun Inisial(nama: String, ukuran: Dp) {
    val huruf = nama.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    Box(Modifier.size(ukuran).background(BiruMuda, CircleShape), contentAlignment = Alignment.Center) {
        Text(huruf, color = Biru, fontWeight = FontWeight.Bold, fontSize = (ukuran.value / 3).sp)
    }
}

// ---------- Layar 4: Detail Anggota ----------
@Composable
fun DetailAnggota(a: Anggota, onEdit: () -> Unit, onHapus: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Inisial(a.nama, 72.dp)
        Text(a.nama, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(a.jenis, color = Biru, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp).background(BiruMuda, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
        Card(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                listOf(
                    "NIM / NIP" to a.nomorInduk, "Program Studi / Unit" to a.prodi, "Alamat" to a.alamat,
                    "Nomor Telepon" to a.telepon, "Email" to a.email
                ).forEach { (label, isi) ->
                    Text(label, fontSize = 12.sp, color = Color.Gray)
                    Text(isi, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onEdit, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Kuning)) { Text("Edit") }
            Button(onClick = onHapus, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Merah)) { Text("Hapus") }
        }
    }
}