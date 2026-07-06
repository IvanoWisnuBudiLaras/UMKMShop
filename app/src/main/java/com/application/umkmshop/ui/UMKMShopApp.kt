package com.application.umkmshop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.application.umkmshop.navigation.AppDestination
import com.application.umkmshop.navigation.UserMode
import com.application.umkmshop.navigation.startRoute
import com.application.umkmshop.ui.chat.ChatListScreen
import com.application.umkmshop.ui.chat.ChatRoomScreen
import com.application.umkmshop.ui.chat.logic.ChatViewModel
import com.application.umkmshop.ui.auth.AuthFormState
import com.application.umkmshop.ui.auth.AuthViewModel
import com.application.umkmshop.ui.product.BuyerCatalogScreen
import com.application.umkmshop.ui.product.FavoriteProductsScreen
import com.application.umkmshop.ui.product.BuyerProductDetailScreen
import com.application.umkmshop.ui.product.ProductFormScreen
import com.application.umkmshop.ui.product.SellerDashboardScreen
import com.application.umkmshop.ui.product.logic.*
import com.application.umkmshop.ui.order.OrderHistoryScreen
import com.application.umkmshop.ui.order.logic.OrderViewModel
import com.application.umkmshop.ui.notification.InboxScreen
import com.application.umkmshop.ui.notification.logic.InboxUiState
import com.application.umkmshop.ui.notification.logic.InboxViewModel
import androidx.compose.ui.platform.LocalInspectionMode
import com.application.umkmshop.ui.profile.ProfileScreen
import com.application.umkmshop.ui.profile.logic.ProfileViewModel
import com.application.umkmshop.ui.components.*
import com.application.umkmshop.ui.theme.UMKMShopTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UMKMShopApp(
    modifier: Modifier = Modifier,
    onSignedInForNotifications: () -> Unit = {},
) {
    val authViewModel: AuthViewModel = viewModel()
    val inboxViewModel: InboxViewModel = viewModel()
    val authState by authViewModel.state.collectAsState()
    val inboxState by inboxViewModel.state.collectAsState()

    UMKMShopAppContent(
        authState = authState,
        inboxState = inboxState,
        onSignedInForNotifications = onSignedInForNotifications,
        onInboxRefresh = inboxViewModel::refresh,
        onInboxStartRealtime = inboxViewModel::startRealtime,
        onInboxStopRealtime = inboxViewModel::stopRealtime,
        onAuthSetName = authViewModel::setName,
        onAuthSetEmail = authViewModel::setEmail,
        onAuthSetPassword = authViewModel::setPassword,
        onAuthSetSignup = authViewModel::setSignup,
        onAuthSubmit = authViewModel::submit,
        onAuthLogout = { onLoggedOut -> authViewModel.logout(onLoggedOut) },
        inboxViewModel = inboxViewModel,
        modifier = modifier
    )
}

@Composable
fun UMKMShopAppContent(
    authState: AuthFormState,
    inboxState: InboxUiState,
    onSignedInForNotifications: () -> Unit,
    onInboxRefresh: () -> Unit,
    onInboxStartRealtime: () -> Unit,
    onInboxStopRealtime: () -> Unit,
    onAuthSetName: (String) -> Unit,
    onAuthSetEmail: (String) -> Unit,
    onAuthSetPassword: (String) -> Unit,
    onAuthSetSignup: (Boolean) -> Unit,
    onAuthSubmit: () -> Unit,
    onAuthLogout: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    inboxViewModel: InboxViewModel? = null,
) {
    val navController = rememberNavController()
    var userMode by rememberSaveable { mutableStateOf(UserMode.Buyer) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showAppChrome = authState.session.isSignedIn && currentRoute != AppDestination.Auth.route

    LaunchedEffect(authState.session.isSignedIn) {
        if (authState.session.isSignedIn) {
            onSignedInForNotifications()
            onInboxRefresh()
            onInboxStartRealtime()
        } else {
            onInboxStopRealtime()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (showAppChrome) {
                UMKMTopBar(
                    title = "UMKMShop",
                )
            }
        },
        bottomBar = {
            if (showAppChrome) {
                UMKMBottomBar(
                    currentRoute = currentRoute,
                    userMode = userMode,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(userMode.startRoute) {
                                saveState = true
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Auth.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Auth.route) {
                AuthShell(
                    state = authState,
                    onNameChange = onAuthSetName,
                    onEmailChange = onAuthSetEmail,
                    onPasswordChange = onAuthSetPassword,
                    onModeChange = onAuthSetSignup,
                    onSubmit = onAuthSubmit,
                    onEnterApp = {
                        navController.navigate(AppDestination.BuyerCatalog.route) {
                            popUpTo(AppDestination.Auth.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(AppDestination.BuyerCatalog.route) {
                if (!LocalInspectionMode.current) {
                    val buyerCatalogViewModel: BuyerCatalogViewModel = viewModel()
                    BuyerCatalogScreen(
                        viewModel = buyerCatalogViewModel,
                        onOpenDetail = { productId ->
                            navController.navigate(AppDestination.ProductDetail.createRoute(productId))
                        },
                    )
                }
            }
            composable(AppDestination.Favorites.route) {
                if (!LocalInspectionMode.current) {
                    val buyerCatalogEntry = remember(navController) {
                        navController.getBackStackEntry(AppDestination.BuyerCatalog.route)
                    }
                    val buyerCatalogViewModel: BuyerCatalogViewModel = viewModel(
                        viewModelStoreOwner = buyerCatalogEntry,
                    )
                    FavoriteProductsScreen(
                        viewModel = buyerCatalogViewModel,
                        onOpenDetail = { productId ->
                            navController.navigate(AppDestination.ProductDetail.createRoute(productId))
                        },
                    )
                }
            }
            composable(AppDestination.SellerDashboard.route) {
                if (!LocalInspectionMode.current) {
                    val sellerProductViewModel: SellerProductViewModel = viewModel()
                    LaunchedEffect(authState.session.userId) {
                        if (authState.session.isSignedIn) {
                            sellerProductViewModel.refreshProducts()
                        }
                    }
                    SellerDashboardScreen(
                        viewModel = sellerProductViewModel,
                        onAddProduct = { navController.navigate(AppDestination.ProductForm.route) },
                        onEditProduct = { navController.navigate(AppDestination.ProductForm.route) },
                        onOpenChats = { navController.navigate(AppDestination.ChatList.route) },
                    )
                }
            }
            composable(AppDestination.ProductForm.route) {
                if (!LocalInspectionMode.current) {
                    val sellerDashboardEntry = remember(navController) {
                        navController.getBackStackEntry(AppDestination.SellerDashboard.route)
                    }
                    val sellerProductViewModel: SellerProductViewModel = viewModel(
                        viewModelStoreOwner = sellerDashboardEntry,
                    )
                    ProductFormScreen(
                        viewModel = sellerProductViewModel,
                        onSaved = {
                            navController.navigate(AppDestination.SellerDashboard.route) {
                                popUpTo(AppDestination.SellerDashboard.route) {
                                    inclusive = true
                                }
                            }
                        },
                        onCancel = { navController.navigate(AppDestination.SellerDashboard.route) },
                    )
                }
            }
            composable(
                route = AppDestination.ProductDetail.route,
                arguments = listOf(
                    navArgument(AppDestination.ProductDetail.PRODUCT_ID_ARG) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                if (!LocalInspectionMode.current) {
                    val buyerCatalogEntry = remember(navController) {
                        navController.getBackStackEntry(AppDestination.BuyerCatalog.route)
                    }
                    val buyerCatalogViewModel: BuyerCatalogViewModel = viewModel(
                        viewModelStoreOwner = buyerCatalogEntry,
                    )
                    val productId = backStackEntry.arguments
                        ?.getString(AppDestination.ProductDetail.PRODUCT_ID_ARG)
                        .orEmpty()
                    BuyerProductDetailScreen(
                        viewModel = buyerCatalogViewModel,
                        productId = productId,
                        onBack = { navController.navigate(AppDestination.BuyerCatalog.route) },
                        onChatSeller = { selectedProductId ->
                            navController.navigate(AppDestination.ChatRoom.createRoute(selectedProductId))
                        },
                    )
                }
            }
            composable(AppDestination.ChatList.route) {
                if (!LocalInspectionMode.current) {
                    val chatViewModel: ChatViewModel = viewModel()
                    ChatListScreen(
                        viewModel = chatViewModel,
                        onOpenRoom = { roomId, productId ->
                            navController.navigate(AppDestination.ChatRoom.createRoute(roomId, productId))
                        },
                    )
                }
            }
            composable(
                route = AppDestination.ChatRoom.route,
                arguments = listOf(
                    navArgument(AppDestination.ChatRoom.PRODUCT_ID_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(AppDestination.ChatRoom.ROOM_ID_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                if (!LocalInspectionMode.current) {
                    val chatViewModel: ChatViewModel = viewModel()
                    val orderViewModel: OrderViewModel = viewModel()
                    val productId = backStackEntry.arguments
                        ?.getString(AppDestination.ChatRoom.PRODUCT_ID_ARG)
                    val roomId = backStackEntry.arguments
                        ?.getString(AppDestination.ChatRoom.ROOM_ID_ARG)
                    ChatRoomScreen(
                        viewModel = chatViewModel,
                        orderViewModel = orderViewModel,
                        productId = productId,
                        roomId = roomId,
                        onBack = { navController.navigate(AppDestination.ChatList.route) },
                    )
                }
            }
            composable(AppDestination.TransactionHistory.route) {
                if (!LocalInspectionMode.current) {
                    val orderViewModel: OrderViewModel = viewModel()
                    OrderHistoryScreen(
                        viewModel = orderViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(AppDestination.Inbox.route) {
                if (inboxViewModel != null) {
                    InboxScreen(
                        viewModel = inboxViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(AppDestination.Profile.route) {
                if (!LocalInspectionMode.current) {
                    val profileViewModel: ProfileViewModel = viewModel()
                    ProfileScreen(
                        session = authState.session,
                        viewModel = profileViewModel,
                        unreadInboxCount = inboxState.unreadCount,
                        isSellerMode = userMode == UserMode.Seller,
                        onModeChange = { isSeller ->
                            val nextMode = if (isSeller) UserMode.Seller else UserMode.Buyer
                            userMode = nextMode
                            navController.navigate(nextMode.startRoute) {
                                launchSingleTop = true
                                popUpTo(AppDestination.BuyerCatalog.route) {
                                    inclusive = false
                                    saveState = true
                                }
                                restoreState = true
                            }
                        },
                        onOpenInbox = {
                            navController.navigate(AppDestination.Inbox.route)
                        },
                        onOpenFavorites = {
                            navController.navigate(AppDestination.Favorites.route)
                        },
                        onOpenTransactions = {
                            navController.navigate(AppDestination.TransactionHistory.route)
                        },
                        onLogout = {
                            onAuthLogout {
                                navController.navigate(AppDestination.Auth.route) {
                                    popUpTo(AppDestination.BuyerCatalog.route) { inclusive = true }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UMKMBottomBar(
    currentRoute: String?,
    userMode: UserMode,
    onNavigate: (AppDestination) -> Unit,
) {
    val items = when (userMode) {
        UserMode.Buyer -> listOf(
            AppDestination.BuyerCatalog,
            AppDestination.ChatList,
            AppDestination.Profile,
        )
        UserMode.Seller -> listOf(
            AppDestination.SellerDashboard,
            AppDestination.ChatList,
            AppDestination.Profile,
        )
    }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        items.forEach { destination ->
            val isSelected = currentRoute == destination.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                label = { 
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ) 
                },
                icon = { 
                    val icon = when(destination) {
                        AppDestination.BuyerCatalog -> Icons.Default.ShoppingCart
                        AppDestination.Favorites -> Icons.Default.Favorite
                        AppDestination.Inbox -> Icons.Default.Notifications
                        AppDestination.TransactionHistory -> Icons.Default.Receipt
                        AppDestination.SellerDashboard -> Icons.Default.Store
                        AppDestination.ChatList -> Icons.Default.Chat
                        AppDestination.Profile -> Icons.Default.Person
                        else -> Icons.Default.Home
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = destination.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun AuthShell(
    state: AuthFormState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onEnterApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "UMKMShop",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (state.isSignup) "Gabung dengan komunitas UMKM lokal." else "Selamat datang kembali.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        if (state.session.isRestoring || state.isSubmitting) {
            Spacer(modifier = Modifier.height(20.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (state.isSignup) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Nama") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        state.session.message?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (state.session.isSignedIn) {
            UMKMPrimaryButton(
                onClick = onEnterApp,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Masuk ke Katalog")
            }
        } else {
            UMKMPrimaryButton(
                onClick = onSubmit,
                enabled = !state.isSubmitting && !state.session.isRestoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSignup) "Daftar" else "Login")
            }
            UMKMSecondaryButton(
                onClick = { onModeChange(!state.isSignup) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSignup) "Sudah punya akun? Login" else "Belum punya akun? Daftar")
            }
        }
    }
}

@Composable
private fun ShellScreen(
    title: String,
    description: String,
    primaryAction: String,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onPrimaryAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(primaryAction)
        }
        if (secondaryAction != null && onSecondaryAction != null) {
            TextButton(
                onClick = onSecondaryAction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(secondaryAction)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UMKMShopAppPreview() {
    UMKMShopTheme {
        UMKMShopAppContent(
            authState = AuthFormState(),
            inboxState = InboxUiState(),
            onSignedInForNotifications = {},
            onInboxRefresh = {},
            onInboxStartRealtime = {},
            onInboxStopRealtime = {},
            onAuthSetName = {},
            onAuthSetEmail = {},
            onAuthSetPassword = {},
            onAuthSetSignup = {},
            onAuthSubmit = {},
            onAuthLogout = {},
        )
    }
}
