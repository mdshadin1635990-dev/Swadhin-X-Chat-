package com.example.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.Channel
import com.example.data.model.DirectChat
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import com.example.ui.admin.AdminScreen
import com.example.ui.chat.ChannelChatScreen
import com.example.ui.chat.DirectChatScreen
import com.example.ui.dialogs.CreateChannelDialog
import com.example.ui.dialogs.ReportDialog
import com.example.ui.profile.ProfileDialog
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GoldAdmin
import com.example.ui.theme.VioletSecondary
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentUser: FirebaseUser,
    repository: ChatRepository,
    onSignOut: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val userProfile by repository.observeUserProfile(currentUser.uid)
        .collectAsStateWithLifecycle(initialValue = null)

    val channels by repository.observeChannels()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val directChats by repository.observeDirectChats(currentUser.uid)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val allUsers by repository.observeAllUsers()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val isAdmin = remember(currentUser.email, userProfile?.role) {
        currentUser.email.equals("mdshadin1635990@gmail.com", ignoreCase = true) || userProfile?.role == "admin"
    }

    // Auto-create or sync user profile on login
    LaunchedEffect(currentUser.uid) {
        repository.createOrUpdateProfile(
            displayName = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Community Member",
            photoUrl = currentUser.photoUrl?.toString(),
            statusMessage = "Active on Swadhin X Chat"
        )
    }

    // Seed initial international channels if database is completely empty
    LaunchedEffect(channels.isEmpty()) {
        if (channels.isEmpty() && isAdmin) {
            repository.createChannel(
                name = "Global Lounge",
                description = "Open international community conversation",
                topic = "General",
                icon = "globe",
                isAnnouncementOnly = false
            )
            repository.createChannel(
                name = "Tech & Web Hub",
                description = "Discuss Android, Web, AI, and Cloud",
                topic = "Technology",
                icon = "code",
                isAnnouncementOnly = false
            )
            repository.createChannel(
                name = "Announcements",
                description = "Official updates from Swadhin X Chat admins",
                topic = "Announcements",
                icon = "announcement",
                isAnnouncementOnly = true
            )
        }
    }

    // Navigation and screen states
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Channels, 1: Direct Messages, 2: Community Directory
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    var activeDirectChatId by remember { mutableStateOf<String?>(null) }
    var activeDirectPartner by remember { mutableStateOf<UserProfile?>(null) }
    var showAdminScreen by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Pair<String?, String?>?>(null) } // pair of (userId, messageId)
    var searchQuery by remember { mutableStateOf("") }

    // Sub-screens override
    if (activeChannel != null) {
        ChannelChatScreen(
            channel = activeChannel!!,
            currentUserId = currentUser.uid,
            currentUserName = userProfile?.displayName ?: currentUser.displayName ?: "User",
            currentUserPhotoUrl = userProfile?.photoUrl ?: currentUser.photoUrl?.toString(),
            isAdmin = isAdmin,
            repository = repository,
            onBack = { activeChannel = null },
            onReportMessage = { msgId, senderName ->
                reportTarget = Pair(null, msgId)
            }
        )
        return
    }

    if (activeDirectChatId != null && activeDirectPartner != null) {
        DirectChatScreen(
            chatId = activeDirectChatId!!,
            partnerUser = activeDirectPartner!!,
            currentUserId = currentUser.uid,
            currentUserName = userProfile?.displayName ?: currentUser.displayName ?: "User",
            currentUserPhotoUrl = userProfile?.photoUrl ?: currentUser.photoUrl?.toString(),
            repository = repository,
            onBack = {
                activeDirectChatId = null
                activeDirectPartner = null
            }
        )
        return
    }

    if (showAdminScreen) {
        AdminScreen(
            isAdmin = isAdmin,
            repository = repository,
            onBack = { showAdminScreen = false }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyanPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Swadhin X Chat",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    NavigationRailItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Public, contentDescription = "Channels") },
                        label = { Text("Channels") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Forum, contentDescription = "Direct Chats") },
                        label = { Text("Direct") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Groups, contentDescription = "Members") },
                        label = { Text("Members") }
                    )
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Swadhin X Chat",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(containerColor = CyanPrimary.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        ),
                                        color = CyanPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            if (isAdmin) {
                                IconButton(
                                    onClick = { showAdminScreen = true },
                                    modifier = Modifier.testTag("admin_panel_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = "Admin Panel",
                                        tint = GoldAdmin
                                    )
                                }
                            }

                            // Profile Button with Avatar
                            IconButton(
                                onClick = { showProfileDialog = true },
                                modifier = Modifier.testTag("profile_button")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(CyanPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val photo = userProfile?.photoUrl ?: currentUser.photoUrl?.toString()
                                    if (!photo.isNullOrBlank()) {
                                        AsyncImage(
                                            model = photo,
                                            contentDescription = "Profile",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = (userProfile?.displayName ?: currentUser.displayName ?: "U").take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = CyanPrimary
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Public, contentDescription = "Channels") },
                                label = { Text("Channels") },
                                modifier = Modifier.testTag("nav_channels_tab")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    BadgedBox(badge = {
                                        if (directChats.isNotEmpty()) {
                                            Badge { Text(directChats.size.toString()) }
                                        }
                                    }) {
                                        Icon(Icons.Default.Forum, contentDescription = "Direct Chats")
                                    }
                                },
                                label = { Text("Direct") },
                                modifier = Modifier.testTag("nav_direct_tab")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Groups, contentDescription = "Members") },
                                label = { Text("Members") },
                                modifier = Modifier.testTag("nav_members_tab")
                            )
                        }
                    }
                },
                floatingActionButton = {
                    when (selectedTab) {
                        0 -> {
                            FloatingActionButton(
                                onClick = { showCreateChannelDialog = true },
                                containerColor = CyanPrimary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("create_channel_fab")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Create Channel")
                            }
                        }
                        1, 2 -> {
                            FloatingActionButton(
                                onClick = { selectedTab = 2 },
                                containerColor = VioletSecondary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = "New Chat")
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                when (selectedTab) {
                                    0 -> "Search international channels..."
                                    1 -> "Search direct chats..."
                                    else -> "Search members by name or email..."
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = CyanPrimary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("search_bar"),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    when (selectedTab) {
                        0 -> ChannelsList(
                            channels = channels.filter {
                                searchQuery.isBlank() ||
                                        it.name.contains(searchQuery, ignoreCase = true) ||
                                        it.description.contains(searchQuery, ignoreCase = true) ||
                                        (it.topic?.contains(searchQuery, ignoreCase = true) == true)
                            },
                            onSelectChannel = { activeChannel = it }
                        )
                        1 -> DirectChatsList(
                            directChats = directChats.filter {
                                searchQuery.isBlank() ||
                                        (it.lastMessageText?.contains(searchQuery, ignoreCase = true) == true)
                            },
                            allUsers = allUsers,
                            currentUserId = currentUser.uid,
                            onSelectChat = { chatId, partner ->
                                activeDirectChatId = chatId
                                activeDirectPartner = partner
                            },
                            onExploreUsers = { selectedTab = 2 }
                        )
                        2 -> UsersDirectoryList(
                            users = allUsers.filter {
                                it.userId != currentUser.uid &&
                                        (searchQuery.isBlank() ||
                                                it.displayName.contains(searchQuery, ignoreCase = true) ||
                                                it.email.contains(searchQuery, ignoreCase = true))
                            },
                            onStartDirectChat = { targetUser ->
                                coroutineScope.launch {
                                    val result = repository.getOrCreateDirectChat(targetUser.userId)
                                    if (result.isSuccess) {
                                        activeDirectChatId = result.getOrThrow()
                                        activeDirectPartner = targetUser
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showProfileDialog) {
        ProfileDialog(
            userProfile = userProfile,
            currentUserUid = currentUser.uid,
            currentUserEmail = currentUser.email ?: "",
            isAdmin = isAdmin,
            onDismiss = { showProfileDialog = false },
            onSaveProfile = { name, photo, status ->
                coroutineScope.launch {
                    repository.createOrUpdateProfile(name, photo, status)
                    showProfileDialog = false
                    Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                }
            },
            onSignOut = {
                showProfileDialog = false
                onSignOut()
            }
        )
    }

    if (showCreateChannelDialog) {
        CreateChannelDialog(
            isAdmin = isAdmin,
            onDismiss = { showCreateChannelDialog = false },
            onConfirm = { name, desc, topic, icon, isAnnouncement ->
                coroutineScope.launch {
                    val result = repository.createChannel(name, desc, topic, icon, isAnnouncement)
                    showCreateChannelDialog = false
                    if (result.isSuccess) {
                        Toast.makeText(context, "Channel created!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (reportTarget != null) {
        ReportDialog(
            targetName = if (reportTarget?.second != null) "Message #${reportTarget?.second}" else "User",
            reportedUserId = reportTarget?.first,
            reportedMessageId = reportTarget?.second,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, reportedUid, reportedMsgId ->
                coroutineScope.launch {
                    repository.submitReport(reason, reportedUid, reportedMsgId)
                    reportTarget = null
                    Toast.makeText(context, "Report submitted to admin queue", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun ChannelsList(
    channels: List<Channel>,
    onSelectChannel: (Channel) -> Unit
) {
    if (channels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No channels found. Tap '+' to create one!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelCard(channel = channel, onClick = { onSelectChannel(channel) })
            }
        }
    }
}

@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit
) {
    val iconVector = when (channel.icon) {
        "globe" -> Icons.Default.Public
        "code" -> Icons.Default.Code
        "game" -> Icons.Default.SportsEsports
        "groups" -> Icons.Default.Groups
        "announcement" -> Icons.Default.Campaign
        else -> Icons.Default.Chat
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("channel_item_${channel.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (channel.isAnnouncementOnly) GoldAdmin.copy(alpha = 0.15f) else CyanPrimary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = if (channel.isAnnouncementOnly) GoldAdmin else CyanPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (channel.isAnnouncementOnly) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Announcement",
                            tint = GoldAdmin,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = channel.description.ifBlank { "Tap to open chat" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (!channel.lastMessageText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${channel.lastMessageSenderName ?: "User"}: ${channel.lastMessageText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = channel.topic ?: "General",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DirectChatsList(
    directChats: List<DirectChat>,
    allUsers: List<UserProfile>,
    currentUserId: String,
    onSelectChat: (chatId: String, partner: UserProfile) -> Unit,
    onExploreUsers: () -> Unit
) {
    if (directChats.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = null,
                tint = VioletSecondary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No private chats yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Find members in the community directory and send a direct message!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.clickable { onExploreUsers() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VioletSecondary)
            ) {
                Text(
                    text = "Explore Members Directory",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(directChats, key = { it.id }) { chat ->
                val partnerUid = chat.participantUids.firstOrNull { it != currentUserId } ?: ""
                val partnerProfile = allUsers.firstOrNull { it.userId == partnerUid }
                    ?: UserProfile(userId = partnerUid, displayName = "Chat Member")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectChat(chat.id, partnerProfile) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(VioletSecondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!partnerProfile.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = partnerProfile.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = partnerProfile.displayName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = VioletSecondary,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = partnerProfile.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = chat.lastMessageText ?: "Conversation started",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        if (partnerProfile.isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldOnline)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UsersDirectoryList(
    users: List<UserProfile>,
    onStartDirectChat: (UserProfile) -> Unit
) {
    if (users.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No members found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { it.userId }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(CyanPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!user.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = user.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = user.displayName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = CyanPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.displayName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (user.role == "admin") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "👑 ADMIN",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            ),
                                            color = GoldAdmin
                                        )
                                    }
                                }
                                Text(
                                    text = user.statusMessage ?: user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.clickable { onStartDirectChat(user) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = CyanPrimary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Chat",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = CyanPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
