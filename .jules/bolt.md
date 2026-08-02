## 2024-08-02 - Asynchronous ContentResolver queries in Jetpack Compose
 **Learning:** Calling synchronous blocking methods (like ContentResolver queries) directly inside Jetpack Compose UI code causes main thread jank and blocks recomposition.
 **Action:** Use `LaunchedEffect` with `withContext(Dispatchers.IO)` to offload such queries, and store the resulting data in `mutableStateOf` to safely drive UI updates.
