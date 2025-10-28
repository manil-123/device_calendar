package com.builttoroam.devicecalendar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.CALLER_IS_SYNCADAPTER
import android.provider.CalendarContract.Events
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import com.builttoroam.devicecalendar.common.Constants.Companion.ATTENDEE_EMAIL_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.ATTENDEE_NAME_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.ATTENDEE_PROJECTION
import com.builttoroam.devicecalendar.common.Constants.Companion.ATTENDEE_RELATIONSHIP_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.ATTENDEE_STATUS_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.ATTENDEE_TYPE_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION_ACCESS_LEVEL_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION_ACCOUNT_NAME_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION_ACCOUNT_TYPE_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION_COLOR_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION_DISPLAY_NAME_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION_ID_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION_IS_PRIMARY_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION_OLDER_API
import com.builttoroam.devicecalendar.common.Constants.Companion.CALENDAR_PROJECTION_OWNER_ACCOUNT_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_INSTANCE_DELETION
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_INSTANCE_DELETION_BEGIN_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_INSTANCE_DELETION_END_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_INSTANCE_DELETION_ID_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_INSTANCE_DELETION_LAST_DATE_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_INSTANCE_DELETION_RRULE_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_ALL_DAY_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_AVAILABILITY_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_BEGIN_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_CUSTOM_APP_URI_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_DESCRIPTION_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_END_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_END_TIMEZONE_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_EVENT_LOCATION_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_ID_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_ORIGINAL_ID_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_RECURRING_RULE_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_START_TIMEZONE_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_STATUS_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.EVENT_PROJECTION_TITLE_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.REMINDER_MINUTES_INDEX
import com.builttoroam.devicecalendar.common.Constants.Companion.REMINDER_PROJECTION
import com.builttoroam.devicecalendar.common.DayOfWeek
import com.builttoroam.devicecalendar.common.ErrorCodes.Companion.GENERIC_ERROR
import com.builttoroam.devicecalendar.common.ErrorCodes.Companion.INVALID_ARGUMENT
import com.builttoroam.devicecalendar.common.ErrorCodes.Companion.NOT_ALLOWED
import com.builttoroam.devicecalendar.common.ErrorCodes.Companion.NOT_AUTHORIZED
import com.builttoroam.devicecalendar.common.ErrorCodes.Companion.NOT_FOUND
import com.builttoroam.devicecalendar.common.ErrorMessages
import com.builttoroam.devicecalendar.common.ErrorMessages.Companion.CALENDAR_ID_INVALID_ARGUMENT_NOT_A_NUMBER_MESSAGE
import com.builttoroam.devicecalendar.common.ErrorMessages.Companion.CREATE_EVENT_ARGUMENTS_NOT_VALID_MESSAGE
import com.builttoroam.devicecalendar.common.ErrorMessages.Companion.EVENT_ID_CANNOT_BE_NULL_ON_DELETION_MESSAGE
import com.builttoroam.devicecalendar.common.ErrorMessages.Companion.NOT_AUTHORIZED_MESSAGE
import com.builttoroam.devicecalendar.common.RecurrenceFrequency
import com.builttoroam.devicecalendar.models.*
import com.builttoroam.devicecalendar.models.Calendar
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.*
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.Weekday
import org.dmfs.rfc5545.recur.Freq
import java.text.SimpleDateFormat
import java.util.*

class CalendarDelegate(
    private val _binding: ActivityPluginBinding?,
    private val _context: Context,
    private val channel: MethodChannel
) : PluginRegistry.RequestPermissionsResultListener {

    // UI thread handler for MethodChannel calls (which must run on main thread)
    private val uiThreadHandler = Handler(Looper.getMainLooper())

    // Send diagnostic log to Dart for Firestore upload
    // IMPORTANT: MethodChannel.invokeMethod MUST be called on UI thread
    private fun logDiagnostic(level: String, message: String, data: Map<String, Any> = emptyMap()) {
        uiThreadHandler.post {
            try {
                channel.invokeMethod("onCalendarSyncLog", mapOf(
                    "level" to level,
                    "message" to message,
                    "data" to data
                ))
            } catch (e: Exception) {
                // Silently fail if Dart side not listening - don't break sync
                Log.e("DeviceCalendar", "Failed to send diagnostic log to Dart", e)
            }
        }
    }

    private val RETRIEVE_CALENDARS_REQUEST_CODE = 0
    private val RETRIEVE_EVENTS_REQUEST_CODE = RETRIEVE_CALENDARS_REQUEST_CODE + 1
    private val RETRIEVE_CALENDAR_REQUEST_CODE = RETRIEVE_EVENTS_REQUEST_CODE + 1
    private val CREATE_OR_UPDATE_EVENT_REQUEST_CODE = RETRIEVE_CALENDAR_REQUEST_CODE + 1
    private val DELETE_EVENT_REQUEST_CODE = CREATE_OR_UPDATE_EVENT_REQUEST_CODE + 1
    private val REQUEST_PERMISSIONS_REQUEST_CODE = DELETE_EVENT_REQUEST_CODE + 1
    private val DELETE_CALENDAR_REQUEST_CODE = REQUEST_PERMISSIONS_REQUEST_CODE + 1
    private val PART_TEMPLATE = ";%s="
    private val BYMONTHDAY_PART = "BYMONTHDAY"
    private val BYMONTH_PART = "BYMONTH"
    private val BYSETPOS_PART = "BYSETPOS"

    private val _cachedParametersMap: MutableMap<Int, CalendarMethodsParametersCacheModel> = mutableMapOf()
    private var _gson: Gson? = null

    private var calendarObserver: ContentObserver? = null

    init {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(RecurrenceFrequency::class.java, RecurrenceFrequencySerializer())
        gsonBuilder.registerTypeAdapter(DayOfWeek::class.java, DayOfWeekSerializer())
        gsonBuilder.registerTypeAdapter(Availability::class.java, AvailabilitySerializer())
        gsonBuilder.registerTypeAdapter(EventStatus::class.java, EventStatusSerializer())
        _gson = gsonBuilder.create()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        val permissionGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

        if (!_cachedParametersMap.containsKey(requestCode)) {
            // this plugin doesn't handle this request code
            return false
        }

        val cachedValues: CalendarMethodsParametersCacheModel = _cachedParametersMap[requestCode]
            ?: // unlikely scenario where another plugin is potentially using the same request code but it's not one we are tracking so return to
            // indicate we're not handling the request
            return false

        try {
            if (!permissionGranted) {
                finishWithError(NOT_AUTHORIZED, NOT_AUTHORIZED_MESSAGE, cachedValues.pendingChannelResult)
                return false
            }

            when (cachedValues.calendarDelegateMethodCode) {
                RETRIEVE_CALENDARS_REQUEST_CODE -> {
                    retrieveCalendars(cachedValues.pendingChannelResult)
                }
                RETRIEVE_EVENTS_REQUEST_CODE -> {
                    retrieveEvents(cachedValues.calendarId, cachedValues.calendarEventsStartDate, cachedValues.calendarEventsEndDate, cachedValues.calendarEventsIds, cachedValues.pendingChannelResult)
                }
                RETRIEVE_CALENDAR_REQUEST_CODE -> {
                    retrieveCalendar(cachedValues.calendarId, cachedValues.pendingChannelResult)
                }
                CREATE_OR_UPDATE_EVENT_REQUEST_CODE -> {
                    createOrUpdateEvent(cachedValues.calendarId, cachedValues.event, cachedValues.pendingChannelResult)
                }
                DELETE_EVENT_REQUEST_CODE -> {
                    deleteEvent(cachedValues.calendarId, cachedValues.eventId, cachedValues.pendingChannelResult)
                }
                REQUEST_PERMISSIONS_REQUEST_CODE -> {
                    finishWithSuccess(permissionGranted, cachedValues.pendingChannelResult)
                }
                DELETE_CALENDAR_REQUEST_CODE -> {
                    deleteCalendar(cachedValues.calendarId,cachedValues.pendingChannelResult)
                }
            }

            return true
        } finally {
            _cachedParametersMap.remove(cachedValues.calendarDelegateMethodCode)
        }
    }

    fun requestPermissions(pendingChannelResult: MethodChannel.Result) {
        if (arePermissionsGranted()) {
            finishWithSuccess(true, pendingChannelResult)
        } else {
            val parameters = CalendarMethodsParametersCacheModel(pendingChannelResult, REQUEST_PERMISSIONS_REQUEST_CODE)
            requestPermissions(parameters)
        }
    }

    fun hasPermissions(pendingChannelResult: MethodChannel.Result) {
        finishWithSuccess(arePermissionsGranted(), pendingChannelResult)
    }

    @SuppressLint("MissingPermission")
    fun retrieveCalendars(pendingChannelResult: MethodChannel.Result) {
        if (arePermissionsGranted()) {
            val contentResolver: ContentResolver? = _context?.contentResolver
            val uri: Uri = CalendarContract.Calendars.CONTENT_URI
            val cursor: Cursor? = if (atLeastAPI(17)) {
                contentResolver?.query(uri, CALENDAR_PROJECTION, null, null, null)
            } else {
                contentResolver?.query(uri, CALENDAR_PROJECTION_OLDER_API, null, null, null)
            }
            val calendars: MutableList<Calendar> = mutableListOf()
            try {
                while (cursor?.moveToNext() == true) {
                    val calendar = parseCalendarRow(cursor) ?: continue
                    calendars.add(calendar)
                }

                finishWithSuccess(_gson?.toJson(calendars), pendingChannelResult)
            } catch (e: Exception) {
                finishWithError(GENERIC_ERROR, e.message, pendingChannelResult)
            } finally {
                cursor?.close()
            }
        } else {
            val parameters = CalendarMethodsParametersCacheModel(pendingChannelResult, RETRIEVE_CALENDARS_REQUEST_CODE)
            requestPermissions(parameters)
        }
    }

    private fun retrieveCalendar(calendarId: String, pendingChannelResult: MethodChannel.Result, isInternalCall: Boolean = false): Calendar? {
        if (isInternalCall || arePermissionsGranted()) {
            val calendarIdNumber = calendarId.toLongOrNull()
            if (calendarIdNumber == null) {
                if (!isInternalCall) {
                    finishWithError(INVALID_ARGUMENT, CALENDAR_ID_INVALID_ARGUMENT_NOT_A_NUMBER_MESSAGE, pendingChannelResult)
                }
                return null
            }

            val contentResolver: ContentResolver? = _context?.contentResolver
            val uri: Uri = CalendarContract.Calendars.CONTENT_URI

            val cursor: Cursor? = if (atLeastAPI(17)) {
                contentResolver?.query(ContentUris.withAppendedId(uri, calendarIdNumber), CALENDAR_PROJECTION, null, null, null)
            } else {
                contentResolver?.query(ContentUris.withAppendedId(uri, calendarIdNumber), CALENDAR_PROJECTION_OLDER_API, null, null, null)
            }

            try {
                if (cursor?.moveToFirst() == true) {
                    val calendar = parseCalendarRow(cursor)
                    if (isInternalCall) {
                        return calendar
                    } else {
                        finishWithSuccess(_gson?.toJson(calendar), pendingChannelResult)
                    }
                } else {
                    if (!isInternalCall) {
                        finishWithError(NOT_FOUND, "The calendar with the ID $calendarId could not be found", pendingChannelResult)
                    }
                }
            } catch (e: Exception) {
                finishWithError(GENERIC_ERROR, e.message, pendingChannelResult)
            } finally {
                cursor?.close()
            }
        } else {
            val parameters = CalendarMethodsParametersCacheModel(pendingChannelResult, RETRIEVE_CALENDAR_REQUEST_CODE, calendarId)
            requestPermissions(parameters)
        }

        return null
    }

    fun deleteCalendar(calendarId: String, pendingChannelResult: MethodChannel.Result, isInternalCall: Boolean = false): Calendar? {
        if (isInternalCall || arePermissionsGranted()) {
            val calendarIdNumber = calendarId.toLongOrNull()
            if (calendarIdNumber == null) {
                if (!isInternalCall) {
                    finishWithError(INVALID_ARGUMENT, CALENDAR_ID_INVALID_ARGUMENT_NOT_A_NUMBER_MESSAGE, pendingChannelResult)
                }
                return null
            }

            val contentResolver: ContentResolver? = _context?.contentResolver

            val calendar = retrieveCalendar(calendarId,pendingChannelResult,true);
            if(calendar != null) {
                val calenderUriWithId = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarIdNumber)
                val deleteSucceeded = contentResolver?.delete(calenderUriWithId, null, null) ?: 0
                finishWithSuccess(deleteSucceeded > 0, pendingChannelResult)
            }else {
                if (!isInternalCall) {
                    finishWithError(NOT_FOUND, "The calendar with the ID $calendarId could not be found", pendingChannelResult)
                }
            }
        } else {
            val parameters = CalendarMethodsParametersCacheModel(
                pendingChannelResult = pendingChannelResult,
                calendarDelegateMethodCode = DELETE_CALENDAR_REQUEST_CODE,
                calendarId = calendarId)
            requestPermissions(parameters)
        }

        return null
    }

    fun createCalendar(calendarName: String, calendarColor: String?, localAccountName: String, pendingChannelResult: MethodChannel.Result) {
        val contentResolver: ContentResolver? = _context?.contentResolver

        var uri = CalendarContract.Calendars.CONTENT_URI
        uri = uri.buildUpon()
            .appendQueryParameter(CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, localAccountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues()
        values.put(CalendarContract.Calendars.NAME, calendarName)
        values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendarName)
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, localAccountName)
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        values.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, Color.parseColor((calendarColor
            ?: "0xFFFF0000").replace("0x", "#"))) // Red colour as a default
        values.put(CalendarContract.Calendars.OWNER_ACCOUNT, localAccountName)
        values.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, java.util.Calendar.getInstance().timeZone.id)

        val result = contentResolver?.insert(uri, values)
        // Get the calendar ID that is the last element in the Uri
        val calendarId = java.lang.Long.parseLong(result?.lastPathSegment!!)

        finishWithSuccess(calendarId.toString(), pendingChannelResult)
    }

    fun retrieveEvents(calendarId: String, startDate: Long?, endDate: Long?, eventIds: List<String>, pendingChannelResult: MethodChannel.Result) {
        if (startDate == null && endDate == null && eventIds.isEmpty()) {
            finishWithError(INVALID_ARGUMENT, ErrorMessages.RETRIEVE_EVENTS_ARGUMENTS_NOT_VALID_MESSAGE, pendingChannelResult)
            return
        }

        if (arePermissionsGranted()) {
            val calendar = retrieveCalendar(calendarId, pendingChannelResult, true)
            if (calendar == null) {
                finishWithError(NOT_FOUND, "Couldn't retrieve the Calendar with ID $calendarId", pendingChannelResult)
                return
            }

            val contentResolver: ContentResolver? = _context?.contentResolver
            val eventsUriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(eventsUriBuilder, startDate ?: Date(0).time)
            ContentUris.appendId(eventsUriBuilder, endDate ?: Date(Long.MAX_VALUE).time)

            val eventsUri = eventsUriBuilder.build()
            val eventsCalendarQuery = "(${CalendarContract.Instances.CALENDAR_ID} = $calendarId)"
            val eventsNotDeletedQuery = "(${CalendarContract.Instances.EVENT_ID} IN (SELECT ${CalendarContract.Events._ID} FROM ${CalendarContract.Events.CONTENT_URI.lastPathSegment} WHERE ${CalendarContract.Events.DELETED} != 1))"
            val eventsIdsQuery = "(${CalendarContract.Instances.EVENT_ID} IN (${eventIds.joinToString()}))"

            var eventsSelectionQuery = "$eventsCalendarQuery AND $eventsNotDeletedQuery"
            if (eventIds.isNotEmpty()) {
                eventsSelectionQuery += " AND ($eventsIdsQuery)"
            }
            val eventsSortOrder = CalendarContract.Instances.BEGIN + " DESC"

            val eventsCursor = contentResolver?.query(eventsUri, EVENT_PROJECTION, eventsSelectionQuery, null, eventsSortOrder)

            val events: MutableList<Event> = mutableListOf()

            val exceptionHandler = CoroutineExceptionHandler { _, exception ->
                uiThreadHandler.post {
                    finishWithError(GENERIC_ERROR, exception.message, pendingChannelResult)
                }
            }

            GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
                // First pass: collect all unique event IDs and original IDs for batch RRULE query
                val eventIds = mutableSetOf<Long>()
                val originalIds = mutableSetOf<Long>()
                while (eventsCursor?.moveToNext() == true) {
                    val eventId = eventsCursor.getLong(EVENT_PROJECTION_ID_INDEX)
                    val originalId = eventsCursor.getString(EVENT_PROJECTION_ORIGINAL_ID_INDEX)
                    eventIds.add(eventId)
                    if (originalId != null) {
                        originalIds.add(originalId.toLong())
                    }
                }

                // Batch query Events table once for all RRULEs (eliminates N individual queries)
                val allIdsToQuery = eventIds.union(originalIds)
                val rruleMap = batchQueryRRules(allIdsToQuery, contentResolver)
                Log.d("DeviceCalendar", "Calendar $calendarId: Batch queried ${rruleMap.size} RRULEs for ${allIdsToQuery.size} unique events")
                logDiagnostic("info", "Batch RRULE query complete", mapOf(
                    "calendar_id" to calendarId,
                    "unique_events_queried" to allIdsToQuery.size,
                    "rrules_found" to rruleMap.size,
                    "rrules_with_data" to rruleMap.count { it.value != null }
                ))

                // Log each event's RRULE query result (for diagnosing which events failed)
                for (eventId in eventIds) {
                    val rrule = rruleMap[eventId]
                    logDiagnostic("debug", "Batch query result for event", mapOf(
                        "event_id" to eventId.toString(),
                        "has_rrule" to (rrule != null),
                        "rrule_string" to (rrule?.take(200) ?: "null")
                    ))
                }

                // Reset cursor for second pass
                eventsCursor?.moveToPosition(-1)

                // Second pass: parse events using pre-fetched RRULE map
                var skippedCount = 0
                var totalCount = 0
                val instanceCountPerEvent = mutableMapOf<Long, Int>()

                while (eventsCursor?.moveToNext() == true) {
                    totalCount++
                    val eventId = eventsCursor.getLong(EVENT_PROJECTION_ID_INDEX)
                    instanceCountPerEvent[eventId] = (instanceCountPerEvent[eventId] ?: 0) + 1

                    val event = parseEvent(calendarId, eventsCursor, rruleMap)
                    if (event == null) {
                        skippedCount++
                        continue
                    }
                    events.add(event)
                }

                // Log instance count per unique recurring event
                for ((eventId, count) in instanceCountPerEvent) {
                    if (count > 1) {
                        logDiagnostic("debug", "Recurring event instance count", mapOf(
                            "event_id" to eventId.toString(),
                            "instance_count" to count
                        ))
                    }
                }

                if (skippedCount > 0) {
                    Log.w("DeviceCalendar", "Calendar $calendarId: Skipped $skippedCount of $totalCount events during parsing")
                    logDiagnostic("warning", "Events skipped during parsing", mapOf(
                        "calendar_id" to calendarId,
                        "skipped_count" to skippedCount,
                        "total_count" to totalCount
                    ))
                }
                Log.d("DeviceCalendar", "Calendar $calendarId: Successfully parsed ${events.size} events (skipped $skippedCount)")
                logDiagnostic("info", "Calendar parsing complete", mapOf(
                    "calendar_id" to calendarId,
                    "events_parsed" to events.size,
                    "events_skipped" to skippedCount,
                    "total_queried" to totalCount
                ))

                for (event in events) {
                    val attendees = retrieveAttendees(calendar, event.eventId!!, contentResolver)
                    event.organizer = attendees.firstOrNull { it.isOrganizer != null && it.isOrganizer }
                    event.attendees = attendees
                    event.reminders = retrieveReminders(event.eventId!!, contentResolver)
                }
            }.invokeOnCompletion { cause ->
                eventsCursor?.close()
                if (cause == null) {
                    uiThreadHandler.post {
                        finishWithSuccess(_gson?.toJson(events), pendingChannelResult)
                    }
                }
            }
        } else {
            val parameters = CalendarMethodsParametersCacheModel(pendingChannelResult, RETRIEVE_EVENTS_REQUEST_CODE, calendarId, startDate, endDate)
            requestPermissions(parameters)
        }

        return
    }

    fun createOrUpdateEvent(calendarId: String, event: Event?, pendingChannelResult: MethodChannel.Result) {
        if (arePermissionsGranted()) {
            if (event == null) {
                finishWithError(GENERIC_ERROR, CREATE_EVENT_ARGUMENTS_NOT_VALID_MESSAGE, pendingChannelResult)
                return
            }

            val calendar = retrieveCalendar(calendarId, pendingChannelResult, true)
            if (calendar == null) {
                finishWithError(NOT_FOUND, "Couldn't retrieve the Calendar with ID $calendarId", pendingChannelResult)
                return
            }

            val contentResolver: ContentResolver? = _context?.contentResolver
            val values = buildEventContentValues(event, calendarId)

            val exceptionHandler = CoroutineExceptionHandler { _, exception ->
                uiThreadHandler.post {
                    finishWithError(GENERIC_ERROR, exception.message, pendingChannelResult)
                }
            }

            val job: Job
            var eventId: Long? = event.eventId?.toLongOrNull()
            if (eventId == null) {
                val uri = contentResolver?.insert(Events.CONTENT_URI, values)
                // get the event ID that is the last element in the Uri
                eventId = java.lang.Long.parseLong(uri?.lastPathSegment!!)
                job = GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
                    insertAttendees(event.attendees, eventId, contentResolver)
                    insertReminders(event.reminders, eventId, contentResolver)
                }
            } else {
                job = GlobalScope.launch(Dispatchers.IO + exceptionHandler) {
                    contentResolver?.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null)
                    val existingAttendees = retrieveAttendees(calendar, eventId.toString(), contentResolver)
                    val attendeesToDelete = if (event.attendees.isNotEmpty()) existingAttendees.filter { existingAttendee -> event.attendees.all { it.emailAddress != existingAttendee.emailAddress } } else existingAttendees
                    for (attendeeToDelete in attendeesToDelete) {
                        deleteAttendee(eventId, attendeeToDelete, contentResolver)
                    }

                    val attendeesToInsert = event.attendees.filter { existingAttendees.all { existingAttendee -> existingAttendee.emailAddress != it.emailAddress } }
                    insertAttendees(attendeesToInsert, eventId, contentResolver)
                    deleteExistingReminders(contentResolver, eventId)
                    insertReminders(event.reminders, eventId, contentResolver!!)

                    val existingSelfAttendee = existingAttendees.firstOrNull {
                        it.emailAddress == calendar.ownerAccount
                    }
                    val newSelfAttendee = event.attendees.firstOrNull {
                        it.emailAddress == calendar.ownerAccount
                    }
                    if (existingSelfAttendee != null && newSelfAttendee != null &&
                        newSelfAttendee.attendanceStatus != null &&
                        existingSelfAttendee.attendanceStatus != newSelfAttendee.attendanceStatus) {
                        updateAttendeeStatus(eventId, newSelfAttendee, contentResolver)
                    }
                }
            }
            job.invokeOnCompletion {
                    cause ->
                if (cause == null) {
                    uiThreadHandler.post {
                        finishWithSuccess(eventId.toString(), pendingChannelResult)
                    }
                }
            }
        } else {
            val parameters = CalendarMethodsParametersCacheModel(pendingChannelResult, CREATE_OR_UPDATE_EVENT_REQUEST_CODE, calendarId)
            parameters.event = event
            requestPermissions(parameters)
        }
    }

    fun startCalendarTracking(result: MethodChannel.Result) {
        if (calendarObserver == null) {
            calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    onCalendarEventChange()
                }
            }
            _context?.contentResolver?.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                calendarObserver!!
            )
        }
        result.success(null)
    }

    fun stopCalendarTracking(result: MethodChannel.Result) {
        calendarObserver?.let {
            _context?.contentResolver?.unregisterContentObserver(it)
            calendarObserver = null
        }
        result.success(null)
    }

    private fun onCalendarEventChange() {
        uiThreadHandler.post {
            channel.invokeMethod("onCalendarEventChange", null)
        }
    }

    private fun deleteExistingReminders(contentResolver: ContentResolver?, eventId: Long) {
        val cursor = CalendarContract.Reminders.query(contentResolver, eventId, arrayOf(
            CalendarContract.Reminders._ID
        ))
        while (cursor != null && cursor.moveToNext()) {
            var reminderUri: Uri? = null
            val reminderId = cursor.getLong(0)
            if (reminderId > 0) {
                reminderUri = ContentUris.withAppendedId(CalendarContract.Reminders.CONTENT_URI, reminderId)
            }
            if (reminderUri != null) {
                contentResolver?.delete(reminderUri, null, null)
            }
        }
        cursor?.close()
    }

    @SuppressLint("MissingPermission")
    private fun insertReminders(reminders: List<Reminder>, eventId: Long?, contentResolver: ContentResolver) {
        if (reminders.isEmpty()) {
            return
        }
        val remindersContentValues = reminders.map {
            ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, it.minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
        }.toTypedArray()
        contentResolver.bulkInsert(CalendarContract.Reminders.CONTENT_URI, remindersContentValues)
    }

    private fun buildEventContentValues(event: Event, calendarId: String): ContentValues {
        val values = ContentValues()
        val duration: String? = null
        values.put(Events.ALL_DAY, event.eventAllDay)
        values.put(Events.DTSTART, event.eventStartDate!!)
        values.put(Events.EVENT_TIMEZONE, getTimeZone(event.eventStartTimeZone).id)
        values.put(Events.DTEND, event.eventEndDate!!)
        values.put(Events.EVENT_END_TIMEZONE, getTimeZone(event.eventEndTimeZone).id)
        values.put(Events.TITLE, event.eventTitle)
        values.put(Events.DESCRIPTION, event.eventDescription)
        values.put(Events.EVENT_LOCATION, event.eventLocation)
        values.put(Events.CUSTOM_APP_URI, event.eventURL)
        values.put(Events.CALENDAR_ID, calendarId)
        values.put(Events.DURATION, duration)
        values.put(Events.AVAILABILITY, getAvailability(event.availability))
        values.put(Events.STATUS, getEventStatus(event.eventStatus))

        if (event.recurrenceRule != null) {
            val recurrenceRuleParams = buildRecurrenceRuleParams(event.recurrenceRule!!)
            values.put(Events.RRULE, recurrenceRuleParams)
        }
        return values
    }

    private fun getTimeZone(timeZoneString: String?): TimeZone {
        val deviceTimeZone: TimeZone = java.util.Calendar.getInstance().timeZone
        var timeZone = TimeZone.getTimeZone(timeZoneString ?: deviceTimeZone.id)

        // Invalid time zone names defaults to GMT so update that to be device's time zone
        if (timeZone.id == "GMT" && timeZoneString != "GMT") {
            timeZone = TimeZone.getTimeZone(deviceTimeZone.id)
        }

        return timeZone
    }

    private fun getAvailability(availability: Availability?): Int? = when (availability) {
        Availability.BUSY -> Events.AVAILABILITY_BUSY
        Availability.FREE -> Events.AVAILABILITY_FREE
        Availability.TENTATIVE -> Events.AVAILABILITY_TENTATIVE
        else -> null
    }

    private fun getEventStatus(eventStatus: EventStatus?): Int? = when (eventStatus) {
        EventStatus.CONFIRMED -> Events.STATUS_CONFIRMED
        EventStatus.TENTATIVE -> Events.STATUS_TENTATIVE
        EventStatus.CANCELED -> Events.STATUS_CANCELED
        else -> null
    }

    @SuppressLint("MissingPermission")
    private fun insertAttendees(attendees: List<Attendee>, eventId: Long?, contentResolver: ContentResolver?) {
        if (attendees.isEmpty()) {
            return
        }

        val attendeesValues = attendees.map {
            ContentValues().apply {
                put(CalendarContract.Attendees.ATTENDEE_NAME, it.name)
                put(CalendarContract.Attendees.ATTENDEE_EMAIL, it.emailAddress)
                put(
                    CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                    CalendarContract.Attendees.RELATIONSHIP_ATTENDEE
                )
                put(CalendarContract.Attendees.ATTENDEE_TYPE, it.role)
                put(
                    CalendarContract.Attendees.ATTENDEE_STATUS,
                    it.attendanceStatus
                )
                put(CalendarContract.Attendees.EVENT_ID, eventId)
            }
        }.toTypedArray()

        contentResolver?.bulkInsert(CalendarContract.Attendees.CONTENT_URI, attendeesValues)
    }

    @SuppressLint("MissingPermission")
    private fun deleteAttendee(eventId: Long, attendee: Attendee, contentResolver: ContentResolver?) {
        val selection = "(" + CalendarContract.Attendees.EVENT_ID + " = ?) AND (" + CalendarContract.Attendees.ATTENDEE_EMAIL + " = ?)"
        val selectionArgs = arrayOf(eventId.toString() + "", attendee.emailAddress)
        contentResolver?.delete(CalendarContract.Attendees.CONTENT_URI, selection, selectionArgs)

    }

    private fun updateAttendeeStatus(eventId: Long, attendee: Attendee, contentResolver: ContentResolver?) {
        val selection = "(" + CalendarContract.Attendees.EVENT_ID + " = ?) AND (" + CalendarContract.Attendees.ATTENDEE_EMAIL + " = ?)"
        val selectionArgs = arrayOf(eventId.toString() + "", attendee.emailAddress)
        val values = ContentValues()
        values.put(CalendarContract.Attendees.ATTENDEE_STATUS, attendee.attendanceStatus)
        contentResolver?.update(CalendarContract.Attendees.CONTENT_URI, values, selection, selectionArgs)
    }

    fun deleteEvent(calendarId: String, eventId: String, pendingChannelResult: MethodChannel.Result, startDate: Long? = null, endDate: Long? = null, followingInstances: Boolean? = null) {
        if (arePermissionsGranted()) {
            val existingCal = retrieveCalendar(calendarId, pendingChannelResult, true)
            if (existingCal == null) {
                finishWithError(NOT_FOUND, "The calendar with the ID $calendarId could not be found", pendingChannelResult)
                return
            }

            if (existingCal.isReadOnly) {
                finishWithError(NOT_ALLOWED, "Calendar with ID $calendarId is read-only", pendingChannelResult)
                return
            }

            val eventIdNumber = eventId.toLongOrNull()
            if (eventIdNumber == null) {
                finishWithError(INVALID_ARGUMENT, EVENT_ID_CANNOT_BE_NULL_ON_DELETION_MESSAGE, pendingChannelResult)
                return
            }

            val contentResolver: ContentResolver? = _context?.contentResolver
            if (startDate == null && endDate == null && followingInstances == null) { // Delete all instances
                val eventsUriWithId = ContentUris.withAppendedId(Events.CONTENT_URI, eventIdNumber)
                val deleteSucceeded = contentResolver?.delete(eventsUriWithId, null, null) ?: 0
                finishWithSuccess(deleteSucceeded > 0, pendingChannelResult)
            } else {
                if (!followingInstances!!) { // Only this instance
                    val exceptionUriWithId = ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, eventIdNumber)
                    val values = ContentValues()
                    val instanceCursor = CalendarContract.Instances.query(contentResolver, EVENT_INSTANCE_DELETION, startDate!!, endDate!!)

                    while (instanceCursor.moveToNext()) {
                        val foundEventID = instanceCursor.getLong(EVENT_INSTANCE_DELETION_ID_INDEX)

                        if (eventIdNumber == foundEventID) {
                            values.put(Events.ORIGINAL_INSTANCE_TIME, instanceCursor.getLong(EVENT_INSTANCE_DELETION_BEGIN_INDEX))
                            values.put(Events.STATUS, Events.STATUS_CANCELED)
                        }
                    }

                    val deleteSucceeded = contentResolver?.insert(exceptionUriWithId, values)
                    instanceCursor.close()
                    finishWithSuccess(deleteSucceeded != null, pendingChannelResult)
                } else { // This and following instances
                    val eventsUriWithId = ContentUris.withAppendedId(Events.CONTENT_URI, eventIdNumber)
                    val values = ContentValues()
                    val instanceCursor = CalendarContract.Instances.query(contentResolver, EVENT_INSTANCE_DELETION, startDate!!, endDate!!)

                    while (instanceCursor.moveToNext()) {
                        val foundEventID = instanceCursor.getLong(EVENT_INSTANCE_DELETION_ID_INDEX)

                        if (eventIdNumber == foundEventID) {
                            val newRule = org.dmfs.rfc5545.recur.RecurrenceRule(instanceCursor.getString(EVENT_INSTANCE_DELETION_RRULE_INDEX))
                            val lastDate = instanceCursor.getLong(EVENT_INSTANCE_DELETION_LAST_DATE_INDEX)

                            if (lastDate > 0 && newRule.count != null && newRule.count > 0) { // Update occurrence rule
                                val cursor = CalendarContract.Instances.query(contentResolver, EVENT_INSTANCE_DELETION, startDate, lastDate)
                                while (cursor.moveToNext()) {
                                    if (eventIdNumber == cursor.getLong(EVENT_INSTANCE_DELETION_ID_INDEX)) {
                                        newRule.count--
                                    }
                                }
                                cursor.close()
                            } else { // Indefinite and specified date rule
                                val cursor = CalendarContract.Instances.query(contentResolver, EVENT_INSTANCE_DELETION, startDate - DateUtils.YEAR_IN_MILLIS, startDate - 1)
                                var lastRecurrenceDate: Long? = null

                                while (cursor.moveToNext()) {
                                    if (eventIdNumber == cursor.getLong(EVENT_INSTANCE_DELETION_ID_INDEX)) {
                                        lastRecurrenceDate = cursor.getLong(EVENT_INSTANCE_DELETION_END_INDEX)
                                    }
                                }

                                if (lastRecurrenceDate != null) {
                                    newRule.until = DateTime(lastRecurrenceDate)
                                } else {
                                    newRule.until = DateTime(startDate - 1)
                                }
                                cursor.close()
                            }

                            values.put(Events.RRULE, newRule.toString())
                            contentResolver?.update(eventsUriWithId, values, null, null)
                            finishWithSuccess(true, pendingChannelResult)
                        }
                    }
                    instanceCursor.close()
                }
            }
        } else {
            val parameters = CalendarMethodsParametersCacheModel(pendingChannelResult, DELETE_EVENT_REQUEST_CODE, calendarId)
            parameters.eventId = eventId
            requestPermissions(parameters)
        }
    }

    private fun arePermissionsGranted(): Boolean {
        if (atLeastAPI(23) && _binding != null) {
            val writeCalendarPermissionGranted = _binding!!.activity.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
            val readCalendarPermissionGranted = _binding!!.activity.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            return writeCalendarPermissionGranted && readCalendarPermissionGranted
        }

        return true
    }

    private fun requestPermissions(parameters: CalendarMethodsParametersCacheModel) {
        val requestCode: Int = generateUniqueRequestCodeAndCacheParameters(parameters)
        requestPermissions(requestCode)
    }

    private fun requestPermissions(requestCode: Int) {
        if (atLeastAPI(23)) {
            _binding!!.activity.requestPermissions(arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR), requestCode)
        }
    }

    private fun parseCalendarRow(cursor: Cursor?): Calendar? {
        if (cursor == null) {
            return null
        }

        val calId = cursor.getLong(CALENDAR_PROJECTION_ID_INDEX)
        val displayName = cursor.getString(CALENDAR_PROJECTION_DISPLAY_NAME_INDEX)
        val accessLevel = cursor.getInt(CALENDAR_PROJECTION_ACCESS_LEVEL_INDEX)
        val calendarColor = cursor.getInt(CALENDAR_PROJECTION_COLOR_INDEX)
        val accountName = cursor.getString(CALENDAR_PROJECTION_ACCOUNT_NAME_INDEX)
        val accountType = cursor.getString(CALENDAR_PROJECTION_ACCOUNT_TYPE_INDEX)
        val ownerAccount = cursor.getString(CALENDAR_PROJECTION_OWNER_ACCOUNT_INDEX)

        val calendar = Calendar(
            calId.toString(),
            displayName,
            calendarColor,
            accountName,
            accountType,
            ownerAccount
        )

        calendar.isReadOnly = isCalendarReadOnly(accessLevel)
        if (atLeastAPI(17)) {
            val isPrimary = cursor.getString(CALENDAR_PROJECTION_IS_PRIMARY_INDEX)
            calendar.isDefault = isPrimary == "1"
        } else {
            calendar.isDefault = false
        }
        return calendar
    }

    private fun parseEvent(calendarId: String, cursor: Cursor?, rruleMap: Map<Long, String?>? = null): Event? {
        if (cursor == null) {
            return null
        }

        val eventId = cursor.getLong(EVENT_PROJECTION_ID_INDEX)
        val title = cursor.getString(EVENT_PROJECTION_TITLE_INDEX)
        val description = cursor.getString(EVENT_PROJECTION_DESCRIPTION_INDEX)
        val begin = cursor.getLong(EVENT_PROJECTION_BEGIN_INDEX)
        val end = cursor.getLong(EVENT_PROJECTION_END_INDEX)
        val recurringRule = cursor.getString(EVENT_PROJECTION_RECURRING_RULE_INDEX)
        val allDay = cursor.getInt(EVENT_PROJECTION_ALL_DAY_INDEX) > 0
        val location = cursor.getString(EVENT_PROJECTION_EVENT_LOCATION_INDEX)
        val url = cursor.getString(EVENT_PROJECTION_CUSTOM_APP_URI_INDEX)
        val startTimeZone = cursor.getString(EVENT_PROJECTION_START_TIMEZONE_INDEX)
        val endTimeZone = cursor.getString(EVENT_PROJECTION_END_TIMEZONE_INDEX)
        val availability = parseAvailability(cursor.getInt(EVENT_PROJECTION_AVAILABILITY_INDEX))
        val eventStatus = parseEventStatus(cursor.getInt(EVENT_PROJECTION_STATUS_INDEX))
        val originalId = cursor.getString(EVENT_PROJECTION_ORIGINAL_ID_INDEX)

        // Log when Instances table returns null RRULE (Android quirk - requires fallback query)
        if (recurringRule == null && originalId != null) {
            Log.d("DeviceCalendar", "Event $eventId ('${title?.take(30)}'): RRULE null in Instances, will query Events table (originalId=$originalId)")
        } else if (recurringRule == null && originalId == null) {
            Log.d("DeviceCalendar", "Event $eventId ('${title?.take(30)}'): RRULE null in Instances, will query Events table (first occurrence)")
        }

        val event = Event()
        event.eventTitle = title ?: "New Event"
        event.eventId = eventId.toString()
        event.calendarId = calendarId
        event.eventDescription = description
        event.eventStartDate = begin
        event.eventEndDate = end
        event.eventAllDay = allDay
        event.eventLocation = location
        event.eventURL = url
        
        // RRULE lookup: Use batch-fetched map if available, otherwise fall back to individual queries
        // The batch approach eliminates N individual ContentResolver queries (one per recurring instance)
        if (recurringRule != null) {
            // RRULE provided directly in Instances table cursor
            event.recurrenceRule = parseRecurrenceRuleString(recurringRule)
        } else if (rruleMap != null) {
            // Use pre-fetched batch query result (fast lookup, no additional query)
            val lookupId = if (originalId != null) originalId.toLong() else eventId
            val rruleFromMap = rruleMap[lookupId]
            if (rruleFromMap != null) {
                Log.d("DeviceCalendar", "Event $eventId: Using batch-queried RRULE: ${rruleFromMap.take(50)}")
                event.recurrenceRule = parseRecurrenceRuleString(rruleFromMap)
            } else {
                Log.d("DeviceCalendar", "Event $eventId: No RRULE in batch results - not a recurring event")
            }
        } else {
            // Fallback to individual queries (legacy path for backward compatibility)
            if (originalId != null) {
                // This is a subsequent occurrence, query original event
                Log.d("DeviceCalendar", "Event $eventId: Querying Events table for RRULE (fallback for subsequent occurrence)")
                val originalEventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, originalId.toLong())
                val originalEventCursor = _context?.contentResolver?.query(
                    originalEventUri,
                    arrayOf(CalendarContract.Events.RRULE),
                    null,
                    null,
                    null
                )
                if (originalEventCursor?.moveToFirst() == true) {
                    val originalRecurringRule = originalEventCursor.getString(0)
                    if (originalRecurringRule != null) {
                        Log.d("DeviceCalendar", "Event $eventId: Fallback query succeeded, found RRULE: ${originalRecurringRule.take(50)}")
                    } else {
                        Log.w("DeviceCalendar", "Event $eventId: Fallback query returned null RRULE from Events table")
                    }
                    event.recurrenceRule = parseRecurrenceRuleString(originalRecurringRule)
                } else {
                    Log.w("DeviceCalendar", "Event $eventId: Fallback query failed - cursor empty or null")
                }
                originalEventCursor?.close()
            } else {
                // Check if first occurrence of recurring series
                Log.d("DeviceCalendar", "Event $eventId: Querying Events table for RRULE (fallback for first occurrence)")
                val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                val eventCursor = _context?.contentResolver?.query(
                    eventUri,
                    arrayOf(CalendarContract.Events.RRULE),
                    null,
                    null,
                    null
                )
                if (eventCursor?.moveToFirst() == true) {
                    val eventRecurringRule = eventCursor.getString(0)
                    if (eventRecurringRule != null) {
                        Log.d("DeviceCalendar", "Event $eventId: Fallback query succeeded, found RRULE: ${eventRecurringRule.take(50)}")
                        event.recurrenceRule = parseRecurrenceRuleString(eventRecurringRule)
                    } else {
                        Log.d("DeviceCalendar", "Event $eventId: Fallback query returned null - not a recurring event")
                    }
                } else {
                    Log.w("DeviceCalendar", "Event $eventId: Fallback query failed - cursor empty or null")
                }
                eventCursor?.close()
            }
        }

        // Log final status of recurrence rule parsing
        if (event.recurrenceRule == null && (recurringRule != null || originalId != null)) {
            Log.w("DeviceCalendar", "Event $eventId ('${title?.take(30)}'): Expected recurring event but recurrenceRule is null after all fallback attempts")
        }
        
        event.eventStartTimeZone = startTimeZone
        event.eventEndTimeZone = endTimeZone
        event.availability = availability
        event.eventStatus = eventStatus

        return event
    }

    /**
     * Batch queries the Events table for RRULEs for multiple events at once.
     * Eliminates N individual queries (one per recurring event instance).
     *
     * @param eventIds Set of event IDs to query
     * @param contentResolver ContentResolver to use for query
     * @return Map of eventId -> RRULE string (null if not recurring)
     */
    private fun batchQueryRRules(eventIds: Set<Long>, contentResolver: ContentResolver?): Map<Long, String?> {
        if (eventIds.isEmpty() || contentResolver == null) {
            return emptyMap()
        }

        val rruleMap = mutableMapOf<Long, String?>()

        // Build WHERE clause: _ID IN (123, 456, 789, ...)
        val idsString = eventIds.joinToString(",")
        val selection = "${CalendarContract.Events._ID} IN ($idsString)"

        val cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.RRULE),
            selection,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val eventId = it.getLong(0)
                val rrule = it.getString(1)
                rruleMap[eventId] = rrule
            }
        }

        Log.d("DeviceCalendar", "Batch query: Found RRULEs for ${rruleMap.count { it.value != null }} of ${eventIds.size} events")
        return rruleMap
    }

    private fun parseRecurrenceRuleString(recurrenceRuleString: String?): RecurrenceRule? {
        if (recurrenceRuleString == null) {
            return null
        }

        return try {
            val rfcRecurrenceRule = org.dmfs.rfc5545.recur.RecurrenceRule(recurrenceRuleString)
            val frequency = when (rfcRecurrenceRule.freq) {
                Freq.YEARLY -> RecurrenceFrequency.YEARLY
                Freq.MONTHLY -> RecurrenceFrequency.MONTHLY
                Freq.WEEKLY -> RecurrenceFrequency.WEEKLY
                Freq.DAILY -> RecurrenceFrequency.DAILY
                else -> null
            }

            // Skip events with unsupported frequencies (HOURLY, SECONDLY, etc.)
            if (frequency == null) {
                Log.w("DeviceCalendar", "Skipping event with unsupported frequency: ${rfcRecurrenceRule.freq} in RRULE: $recurrenceRuleString")
                logDiagnostic("warning", "Unsupported RRULE frequency", mapOf(
                    "frequency" to rfcRecurrenceRule.freq.toString(),
                    "rrule" to recurrenceRuleString
                ))
                return null
            }

            val recurrenceRule = RecurrenceRule(frequency)
            if (rfcRecurrenceRule.count != null) {
                recurrenceRule.totalOccurrences = rfcRecurrenceRule.count
            }

            recurrenceRule.interval = rfcRecurrenceRule.interval
            if (rfcRecurrenceRule.until != null) {
                recurrenceRule.endDate = rfcRecurrenceRule.until.timestamp
            }

            when (rfcRecurrenceRule.freq) {
                Freq.WEEKLY, Freq.MONTHLY, Freq.YEARLY -> {
                    recurrenceRule.daysOfWeek = rfcRecurrenceRule.byDayPart?.mapNotNull {
                        DayOfWeek.values().find { dayOfWeek -> dayOfWeek.ordinal == it.weekday.ordinal }
                    }?.toMutableList()
                }
                else -> recurrenceRule.daysOfWeek = null
            }

            val rfcRecurrenceRuleString = rfcRecurrenceRule.toString()
            if (rfcRecurrenceRule.freq == Freq.MONTHLY || rfcRecurrenceRule.freq == Freq.YEARLY) {
                // Get week number value from BYSETPOS
                recurrenceRule.weekOfMonth = convertCalendarPartToNumericValues(rfcRecurrenceRuleString, BYSETPOS_PART)

                // If value is not found in BYSETPOS and not repeating by nth day or nth month
                // Get the week number value from the BYDAY position
                if (recurrenceRule.weekOfMonth == null && !rfcRecurrenceRule.byDayPart.isNullOrEmpty()) {
                    recurrenceRule.weekOfMonth = rfcRecurrenceRule.byDayPart.first().pos
                }

            recurrenceRule.dayOfMonth = convertCalendarPartToNumericValues(rfcRecurrenceRuleString, BYMONTHDAY_PART)

            if (rfcRecurrenceRule.freq == Freq.YEARLY) {
                recurrenceRule.monthOfYear = convertCalendarPartToNumericValues(rfcRecurrenceRuleString, BYMONTH_PART)
            }
        }

        recurrenceRule
        } catch (e: Exception) {
            Log.e("DeviceCalendar", "Failed to parse RRULE string: $recurrenceRuleString", e)
            logDiagnostic("error", "RRULE parsing exception", mapOf(
                "rrule" to recurrenceRuleString,
                "error" to (e.message ?: "unknown error")
            ))
            null
        }
    }

    private fun convertCalendarPartToNumericValues(rfcRecurrenceRuleString: String, partName: String): Int? {
        val partIndex = rfcRecurrenceRuleString.indexOf(partName)
        if (partIndex == -1) {
            return null
        }

        return rfcRecurrenceRuleString.substring(partIndex).split(";").firstOrNull()?.split("=")?.lastOrNull()?.split(",")?.map {
            it.toInt()
        }?.firstOrNull()
    }

    private fun parseAttendeeRow(calendar: Calendar, cursor: Cursor?): Attendee? {
        if (cursor == null) {
            return null
        }

        val emailAddress = cursor.getString(ATTENDEE_EMAIL_INDEX)

        return Attendee(
            emailAddress,
            cursor.getString(ATTENDEE_NAME_INDEX),
            cursor.getInt(ATTENDEE_TYPE_INDEX),
            cursor.getInt(ATTENDEE_STATUS_INDEX),
            cursor.getInt(ATTENDEE_RELATIONSHIP_INDEX) == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER,
            emailAddress == calendar.ownerAccount
        )
    }

    private fun parseReminderRow(cursor: Cursor?): Reminder? {
        if (cursor == null) {
            return null
        }

        return Reminder(cursor.getInt(REMINDER_MINUTES_INDEX))
    }

    private fun isCalendarReadOnly(accessLevel: Int): Boolean {
        return when (accessLevel) {
            Events.CAL_ACCESS_CONTRIBUTOR,
            Events.CAL_ACCESS_ROOT,
            Events.CAL_ACCESS_OWNER,
            Events.CAL_ACCESS_EDITOR
            -> false
            else -> true
        }
    }

    @SuppressLint("MissingPermission")
    private fun retrieveAttendees(calendar: Calendar, eventId: String, contentResolver: ContentResolver?): MutableList<Attendee> {
        val attendees: MutableList<Attendee> = mutableListOf()
        val attendeesQuery = "(${CalendarContract.Attendees.EVENT_ID} = ${eventId})"
        val attendeesCursor = contentResolver?.query(CalendarContract.Attendees.CONTENT_URI, ATTENDEE_PROJECTION, attendeesQuery, null, null)
        attendeesCursor.use { cursor ->
            if (cursor?.moveToFirst() == true) {
                do {
                    val attendee = parseAttendeeRow(calendar, attendeesCursor) ?: continue
                    attendees.add(attendee)
                } while (cursor.moveToNext())
            }
        }

        return attendees
    }

    @SuppressLint("MissingPermission")
    private fun retrieveReminders(eventId: String, contentResolver: ContentResolver?): MutableList<Reminder> {
        val reminders: MutableList<Reminder> = mutableListOf()
        val remindersQuery = "(${CalendarContract.Reminders.EVENT_ID} = ${eventId})"
        val remindersCursor = contentResolver?.query(CalendarContract.Reminders.CONTENT_URI, REMINDER_PROJECTION, remindersQuery, null, null)
        remindersCursor.use { cursor ->
            if (cursor?.moveToFirst() == true) {
                do {
                    val reminder = parseReminderRow(remindersCursor) ?: continue
                    reminders.add(reminder)
                } while (cursor.moveToNext())
            }
        }

        return reminders
    }

    @Synchronized
    private fun generateUniqueRequestCodeAndCacheParameters(parameters: CalendarMethodsParametersCacheModel): Int {
        // TODO we can ran out of Int's at some point so this probably should re-use some of the freed ones
        val uniqueRequestCode: Int = (_cachedParametersMap.keys.maxOrNull() ?: 0) + 1
        parameters.ownCacheKey = uniqueRequestCode
        _cachedParametersMap[uniqueRequestCode] = parameters

        return uniqueRequestCode
    }

    private fun <T> finishWithSuccess(result: T, pendingChannelResult: MethodChannel.Result) {
        pendingChannelResult.success(result)
        clearCachedParameters(pendingChannelResult)
    }

    private fun finishWithError(errorCode: String, errorMessage: String?, pendingChannelResult: MethodChannel.Result) {
        pendingChannelResult.error(errorCode, errorMessage, null)
        clearCachedParameters(pendingChannelResult)
    }

    private fun clearCachedParameters(pendingChannelResult: MethodChannel.Result) {
        val cachedParameters = _cachedParametersMap.values.filter { it.pendingChannelResult == pendingChannelResult }.toList()
        for (cachedParameter in cachedParameters) {
            if (_cachedParametersMap.containsKey(cachedParameter.ownCacheKey)) {
                _cachedParametersMap.remove(cachedParameter.ownCacheKey)
            }
        }
    }

    private fun atLeastAPI(api: Int): Boolean {
        return api <= android.os.Build.VERSION.SDK_INT
    }

    private fun buildRecurrenceRuleParams(recurrenceRule: RecurrenceRule): String {
        val frequencyParam = when (recurrenceRule.recurrenceFrequency) {
            RecurrenceFrequency.DAILY -> Freq.DAILY
            RecurrenceFrequency.WEEKLY -> Freq.WEEKLY
            RecurrenceFrequency.MONTHLY -> Freq.MONTHLY
            RecurrenceFrequency.YEARLY -> Freq.YEARLY
        }
        val rr = org.dmfs.rfc5545.recur.RecurrenceRule(frequencyParam)
        if (recurrenceRule.interval != null) {
            rr.interval = recurrenceRule.interval!!
        }

        if (recurrenceRule.recurrenceFrequency == RecurrenceFrequency.WEEKLY ||
            recurrenceRule.weekOfMonth != null && (recurrenceRule.recurrenceFrequency == RecurrenceFrequency.MONTHLY || recurrenceRule.recurrenceFrequency == RecurrenceFrequency.YEARLY)) {
            rr.byDayPart = buildByDayPart(recurrenceRule)
        }

        if (recurrenceRule.totalOccurrences != null) {
            rr.count = recurrenceRule.totalOccurrences!!
        } else if (recurrenceRule.endDate != null) {
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = recurrenceRule.endDate!!
            val dateFormat = SimpleDateFormat("yyyyMMdd")
            dateFormat.timeZone = calendar.timeZone
            rr.until = DateTime(calendar.timeZone, recurrenceRule.endDate!!)
        }

        var rrString = rr.toString()

        if (recurrenceRule.monthOfYear != null && recurrenceRule.recurrenceFrequency == RecurrenceFrequency.YEARLY) {
            rrString = rrString.addPartWithValues(BYMONTH_PART, recurrenceRule.monthOfYear)
        }

        if (recurrenceRule.recurrenceFrequency == RecurrenceFrequency.MONTHLY || recurrenceRule.recurrenceFrequency == RecurrenceFrequency.YEARLY) {
            if (recurrenceRule.weekOfMonth == null) {
                rrString = rrString.addPartWithValues(BYMONTHDAY_PART, recurrenceRule.dayOfMonth)
            }
        }

        return rrString
    }

    private fun buildByDayPart(recurrenceRule: RecurrenceRule): List<org.dmfs.rfc5545.recur.RecurrenceRule.WeekdayNum>? {
        if (recurrenceRule.daysOfWeek?.isEmpty() == true) {
            return null
        }

        return recurrenceRule.daysOfWeek?.mapNotNull { dayOfWeek ->
            Weekday.values().firstOrNull {
                it.ordinal == dayOfWeek.ordinal
            }
        }?.map {
            org.dmfs.rfc5545.recur.RecurrenceRule.WeekdayNum(recurrenceRule.weekOfMonth ?: 0, it)
        }
    }

    private fun String.addPartWithValues(partName: String, values: Int?): String {
        if (values != null) {
            return this + PART_TEMPLATE.format(partName) + values
        }

        return this
    }

    private fun parseAvailability(availability: Int): Availability? = when (availability) {
        Events.AVAILABILITY_BUSY -> Availability.BUSY
        Events.AVAILABILITY_FREE -> Availability.FREE
        Events.AVAILABILITY_TENTATIVE -> Availability.TENTATIVE
        else -> null
    }

    private fun parseEventStatus(status: Int): EventStatus? = when(status) {
        Events.STATUS_CONFIRMED -> EventStatus.CONFIRMED
        Events.STATUS_CANCELED -> EventStatus.CANCELED
        Events.STATUS_TENTATIVE -> EventStatus.TENTATIVE
        else -> null
    }
}