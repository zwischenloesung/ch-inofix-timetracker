package ch.inofix.timetracker.web.internal.portlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

import com.liferay.document.library.kernel.exception.FileSizeException;
import com.liferay.document.library.kernel.service.DLFileEntryLocalService;
import com.liferay.exportimport.kernel.configuration.ExportImportConfigurationConstants;
import com.liferay.exportimport.kernel.configuration.ExportImportConfigurationSettingsMapFactory;
import com.liferay.exportimport.kernel.lar.ExportImportHelperUtil;
import com.liferay.exportimport.kernel.model.ExportImportConfiguration;
import com.liferay.exportimport.kernel.service.ExportImportConfigurationLocalService;
import com.liferay.portal.kernel.exception.NoSuchResourceException;
import com.liferay.portal.kernel.exception.NoSuchUserException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.portlet.PortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.search.Document;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.Hits;
import com.liferay.portal.kernel.search.SearchException;
import com.liferay.portal.kernel.search.Sort;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.upload.UploadException;
import com.liferay.portal.kernel.upload.UploadPortletRequest;
import com.liferay.portal.kernel.upload.UploadRequestSizeException;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StreamUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.xml.Node;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.thoughtworks.xstream.XStream;

import aQute.bnd.annotation.metatype.Configurable;
import ch.inofix.timetracker.constants.PortletKeys;
import ch.inofix.timetracker.exception.NoSuchTaskRecordException;
import ch.inofix.timetracker.exception.TaskRecordEndDateException;
import ch.inofix.timetracker.exception.TaskRecordStartDateException;
import ch.inofix.timetracker.model.TaskRecord;
import ch.inofix.timetracker.model.impl.TaskRecordImpl;
import ch.inofix.timetracker.service.TaskRecordService;
import ch.inofix.timetracker.web.configuration.TimetrackerConfiguration;
import ch.inofix.timetracker.web.internal.constants.TimetrackerWebKeys;
import ch.inofix.timetracker.web.internal.portlet.util.PortletUtil;

/**
 * View Controller of Inofix' timetracker.
 *
 * @author Christian Berndt, Stefan Luebbers
 * @created 2013-10-07 10:47
 * @modified 2017-04-18 15:38
 * @version 1.6.5
 */
@Component(immediate = true, property = { "com.liferay.portlet.css-class-wrapper=portlet-timetracker",
        "com.liferay.portlet.display-category=category.inofix", "com.liferay.portlet.header-portlet-css=/css/main.css",
        "com.liferay.portlet.instanceable=false", "javax.portlet.display-name=Timetracker",
        "javax.portlet.init-param.template-path=/", "javax.portlet.init-param.view-template=/view.jsp",
        "javax.portlet.resource-bundle=content.Language",
        "javax.portlet.security-role-ref=power-user,user" }, service = Portlet.class)
public class TimetrackerPortlet extends MVCPortlet {

    /**
     * @param actionRequest
     * @param actionResponse
     * @since 1.0.8
     * @throws Exception
     */
    public void deleteGroupTaskRecords(ActionRequest actionRequest, ActionResponse actionResponse) throws Exception {

        String tabs1 = ParamUtil.getString(actionRequest, "tabs1");

        ServiceContext serviceContext = ServiceContextFactory.getInstance(TaskRecord.class.getName(), actionRequest);

        List<TaskRecord> taskRecords = _taskRecordService.deleteGroupTaskRecords(serviceContext.getScopeGroupId());

        SessionMessages.add(actionRequest, REQUEST_PROCESSED,
                PortletUtil.translate("successfully-deleted-x-task-records", taskRecords.size()));

        actionResponse.setRenderParameter("tabs1", tabs1);
    }

    /**
     *
     * @param actionRequest
     * @param actionResponse
     * @throws Exception
     */
    public void deleteTaskRecord(ActionRequest actionRequest, ActionResponse actionResponse) throws Exception {

        long taskRecordId = ParamUtil.getLong(actionRequest, "taskRecordId");

        _taskRecordService.deleteTaskRecord(taskRecordId);
    }

    @Override
    public void doView(RenderRequest renderRequest, RenderResponse renderResponse)
            throws IOException, PortletException {

        renderRequest.setAttribute(TimetrackerConfiguration.class.getName(), _timetrackerConfiguration);

        super.doView(renderRequest, renderResponse);
    }

    public void exportTaskRecords(ActionRequest actionRequest, ActionResponse actionResponse) throws Exception {

        _log.info("exportTaskRecords");

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        long groupId = themeDisplay.getScopeGroupId();
        long userId = themeDisplay.getUserId();

        _log.info(groupId);

        ExportImportConfiguration exportImportConfiguration = getExportImportConfiguration(actionRequest);

        exportImportConfiguration.setName("TaskRecords");

        _log.info(exportImportConfiguration);

        // exportImportConfiguration.setGroupId(groupId);

        long backgroundTaskId = _taskRecordService.exportTaskRecordsAsFileInBackground(userId,
                exportImportConfiguration);

        _log.info("backgroundTaskId = " + backgroundTaskId);

    }

    /**
     * @param resourceRequest
     * @param resourceResponse
     * @throws IOException
     * @throws SearchException
     * @since 1.1.5
     */
    public void getSum(ResourceRequest resourceRequest, ResourceResponse resourceResponse) throws Exception {

        HttpServletRequest request = PortalUtil.getHttpServletRequest(resourceRequest);

        List<TaskRecord> taskRecords = getTaskRecords(request);

        long minutes = 0;

        for (TaskRecord taskRecord : taskRecords) {
            minutes = minutes + taskRecord.getDurationInMinutes();
        }

        double hours = 0;

        if (minutes > 0) {
            hours = ((double) minutes) / 60;
        }

        PortletResponseUtil.write(resourceResponse, String.valueOf(hours));

    }

    /**
     * @param resourceRequest
     * @param resourceResponse
     * @return
     * @since 1.1.6
     * @throws SearchException
     */
    public List<TaskRecord> getTaskRecords(HttpServletRequest request) throws Exception {

        ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);

        String keywords = ParamUtil.getString(request, "keywords");
        String orderByCol = ParamUtil.getString(request, "orderByCol", "modifiedDate");
        String orderByType = ParamUtil.getString(request, "orderByType", "desc");

        boolean reverse = "desc".equals(orderByType);

        Sort sort = new Sort(orderByCol, reverse);

        Hits hits = _taskRecordService.search(themeDisplay.getUserId(), themeDisplay.getScopeGroupId(), keywords, 0,
                Integer.MAX_VALUE, sort);

        List<TaskRecord> taskRecords = new ArrayList<TaskRecord>();

        for (int i = 0; i < hits.getDocs().length; i++) {
            Document doc = hits.doc(i);

            long taskRecordId = GetterUtil.getLong(doc.get(Field.ENTRY_CLASS_PK));

            TaskRecord taskRecord = null;

            try {
                taskRecord = _taskRecordService.getTaskRecord(taskRecordId);
            } catch (PortalException pe) {
                _log.error(pe.getLocalizedMessage());
            } catch (SystemException se) {
                _log.error(se.getLocalizedMessage());
            }

            if (taskRecord != null) {
                taskRecords.add(taskRecord);
            }
        }

        return taskRecords;

    }

    /**
     * @since 1.1.4
     * @param actionRequest
     * @param actionResponse
     * @deprecated use importInBackground instead
     */
    @Deprecated
    public void importXML(ActionRequest actionRequest, ActionResponse actionResponse) throws Exception {

        ServiceContext serviceContext = ServiceContextFactory.getInstance(TaskRecord.class.getName(), actionRequest);

        UploadPortletRequest uploadPortletRequest = PortalUtil.getUploadPortletRequest(actionRequest);

        File file = uploadPortletRequest.getFile("file");

        if (Validator.isNotNull(file)) {

            com.liferay.portal.kernel.xml.Document document = SAXReaderUtil.read(file);

            List<Node> nodes = document.selectNodes("/taskRecords/" + TaskRecordImpl.class.getName());

            int numRecords = 0;

            XStream xstream = new XStream();

            ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

            long groupId = themeDisplay.getScopeGroupId();
            long userId = themeDisplay.getUserId();
            User user = UserLocalServiceUtil.getUser(userId);
            String userName = user.getFullName();

            for (Node node : nodes) {

                String xml = node.asXML();

                TaskRecord importRecord = (TaskRecord) xstream.fromXML(xml);

                long taskRecordId = importRecord.getTaskRecordId();
                long companyId = PortalUtil.getCompanyId(actionRequest);

                if (companyId != importRecord.getCompanyId()) {

                    // Data is not from this portal instance
                    importRecord.setCompanyId(companyId);
                }

                if (groupId != importRecord.getGroupId()) {

                    // Data is not from this group
                    importRecord.setGroupId(groupId);
                }

                User systemUser = null;
                try {
                    systemUser = UserLocalServiceUtil.getUser(importRecord.getUserId());
                } catch (NoSuchUserException nsue) {
                    _log.warn(nsue.getMessage());
                }

                // if (systemUser == null) {

                // The record's user does not exist in this system.
                // Use the current user's id and userName instead.
                importRecord.setUserId(userId);
                importRecord.setUserName(userName);

                // } else {
                //
                // // Update the record with the system user's userName
                // importRecord.setUserName(systemUser.getFullName());
                // }

                TaskRecord existingRecord = null;

                try {
                    existingRecord = _taskRecordService.getTaskRecord(taskRecordId);
                } catch (NoSuchTaskRecordException ignore) {
                }

                if (existingRecord == null) {

                    // Insert the imported record as new

                    try {
                        _taskRecordService.addTaskRecord(importRecord.getWorkPackage(), importRecord.getDescription(),
                                importRecord.getTicketURL(), importRecord.getEndDate(), importRecord.getStartDate(),
                                importRecord.getStatus(), importRecord.getDuration(), serviceContext);
                    } catch (Exception e) {
                        _log.error(e);
                    }

                }

                numRecords++;
            }

            SessionMessages.add(actionRequest, REQUEST_PROCESSED,
                    PortletUtil.translate("successfully-imported-x-task-records", numRecords));
        } else {
            SessionErrors.add(actionRequest, PortletUtil.translate("file-not-found"));
        }

    }

    /**
     * From ImportLayoutsMVCCommand
     *
     * @param actionRequest
     * @param actionResponse
     * @throws Exception
     */
//    @Override
//    public void processAction(ActionRequest actionRequest, ActionResponse actionResponse) {
//
//        _log.info("processAction()");
//
//        String cmd = ParamUtil.getString(actionRequest, Constants.CMD);
//
//        _log.info("cmd = " + cmd);
//
//        try {
//            if (cmd.equals(Constants.ADD_TEMP)) {
//
//                addTempFileEntry(actionRequest, ExportImportHelper.TEMP_FOLDER_NAME);
//
//                // TODO
//                // validateFile(actionRequest, actionResponse,
//                // ExportImportHelper.TEMP_FOLDER_NAME);
//
//                hideDefaultSuccessMessage(actionRequest);
//            } else if (cmd.equals(Constants.DELETE_TEMP)) {
//
//                // TODO
//                // deleteTempFileEntry(actionRequest, actionResponse,
//                // ExportImportHelper.TEMP_FOLDER_NAME);
//
//                hideDefaultSuccessMessage(actionRequest);
//            } else if (cmd.equals(Constants.IMPORT)) {
//
//                _log.info("cmd = " + cmd);
//
//                hideDefaultSuccessMessage(actionRequest);
//
//                importData(actionRequest, ExportImportHelper.TEMP_FOLDER_NAME);
//
//                String redirect = ParamUtil.getString(actionRequest, "redirect");
//                _log.info("redirect = " + redirect);
//
//                // super.sendRedirect(actionRequest, actionResponse);
//                // sendRedirect(actionRequest, actionResponse, redirect);
//            }
//        } catch (Exception e) {
//            if (cmd.equals(Constants.ADD_TEMP) || cmd.equals(Constants.DELETE_TEMP)) {
//
//                hideDefaultSuccessMessage(actionRequest);
//
//                // TODO
//                // handleUploadException(actionRequest, actionResponse,
//                // ExportImportHelper.TEMP_FOLDER_NAME, e);
//            } else {
//                if ((e instanceof LARFileException) || (e instanceof LARFileSizeException)
//                        || (e instanceof LARTypeException)) {
//
//                    SessionErrors.add(actionRequest, e.getClass());
//                } else if ((e instanceof LayoutPrototypeException) || (e instanceof LocaleException)) {
//
//                    SessionErrors.add(actionRequest, e.getClass(), e);
//                } else {
//                    _log.error(e, e);
//
//                    SessionErrors.add(actionRequest, LayoutImportException.class.getName());
//                }
//            }
//        }
//    }

    @Override
    public void render(RenderRequest renderRequest, RenderResponse renderResponse)
            throws IOException, PortletException {

        try {
            getTaskRecord(renderRequest);
        } catch (Exception e) {
            if (e instanceof NoSuchResourceException || e instanceof PrincipalException) {
                SessionErrors.add(renderRequest, e.getClass());
            } else {
                throw new PortletException(e);
            }
        }

        super.render(renderRequest, renderResponse);
    }

    @Override
    public void serveResource(ResourceRequest resourceRequest, ResourceResponse resourceResponse)
            throws PortletException {

        try {
            String resourceID = resourceRequest.getResourceID();

            if (resourceID.equals("getSum")) {
                getSum(resourceRequest, resourceResponse);
            } else {
                super.serveResource(resourceRequest, resourceResponse);
            }
        } catch (Exception e) {
            throw new PortletException(e);
        }
    }

    /**
     *
     * @param actionRequest
     * @param actionResponse
     * @throws Exception
     */
    public void updateTaskRecord(ActionRequest actionRequest, ActionResponse actionResponse) throws Exception {

        long taskRecordId = ParamUtil.getLong(actionRequest, "taskRecordId");

        ServiceContext serviceContext = ServiceContextFactory.getInstance(TaskRecord.class.getName(), actionRequest);

        String workPackage = ParamUtil.getString(actionRequest, "workPackage");
        String description = ParamUtil.getString(actionRequest, "description");
        String ticketURL = ParamUtil.getString(actionRequest, "ticketURL");
        int durationInMinutes = ParamUtil.getInteger(actionRequest, "duration");
        long duration = durationInMinutes * 60 * 1000;
        int status = ParamUtil.getInteger(actionRequest, "status");

        int fromDateDay = ParamUtil.getInteger(actionRequest, "fromDateDay");
        int fromDateMonth = ParamUtil.getInteger(actionRequest, "fromDateMonth");
        int fromDateYear = ParamUtil.getInteger(actionRequest, "fromDateYear");
        int fromDateHour = ParamUtil.getInteger(actionRequest, "fromDateHour");
        int fromDateMinute = ParamUtil.getInteger(actionRequest, "fromDateMinute");

        // TODO: clean this up!
        // Create the untilDate with the date values of
        // the fromDate, because we want the user to
        // have to select only one date.
        int untilDateDay = ParamUtil.getInteger(actionRequest, "fromDateDay");
        int untilDateMonth = ParamUtil.getInteger(actionRequest, "fromDateMonth");
        int untilDateYear = ParamUtil.getInteger(actionRequest, "fromDateYear");
        int untilDateHour = ParamUtil.getInteger(actionRequest, "untilDateHour");
        int untilDateMinute = ParamUtil.getInteger(actionRequest, "untilDateMinute");

        Date fromDate = null;

        try {
            fromDate = PortalUtil.getDate(fromDateMonth, fromDateDay, fromDateYear, fromDateHour, fromDateMinute,
                    TaskRecordStartDateException.class);
        } catch (Exception e) {
            _log.error(e);
        }

        Date untilDate = null;

        try {
            untilDate = PortalUtil.getDate(untilDateMonth, untilDateDay, untilDateYear, untilDateHour, untilDateMinute,
                    TaskRecordEndDateException.class);
        } catch (Exception e) {
            _log.error(e);
        }

        long fromTime = fromDate.getTime();
        long untilTime = untilDate.getTime();

        if (duration == 0) {
            duration = untilTime - fromTime;
        }

        TaskRecord taskRecord = null;

        if (taskRecordId <= 0) {

            // Add taskRecord

            taskRecord = _taskRecordService.addTaskRecord(workPackage, description, ticketURL, untilDate, fromDate,
                    status, duration, serviceContext);

        } else {

            // Update taskRecord

            taskRecord = _taskRecordService.updateTaskRecord(taskRecordId, workPackage, description, ticketURL,
                    untilDate, fromDate, status, duration, serviceContext);
        }

        String redirect = getEditTaskRecordURL(actionRequest, actionResponse, taskRecord);

        actionRequest.setAttribute(WebKeys.REDIRECT, redirect);
    }

    @Activate
    @Modified
    protected void activate(Map<Object, Object> properties) {
        _timetrackerConfiguration = Configurable.createConfigurable(TimetrackerConfiguration.class, properties);
    }

    protected void addTempFileEntry(ActionRequest actionRequest, String folderName) throws Exception {

        UploadPortletRequest uploadPortletRequest = PortalUtil.getUploadPortletRequest(actionRequest);

        // TODO
        // checkExceededSizeLimit(uploadPortletRequest);

        long groupId = ParamUtil.getLong(actionRequest, "groupId");

        deleteTempFileEntry(groupId, folderName);

        InputStream inputStream = null;

        try {
            String sourceFileName = uploadPortletRequest.getFileName("file");

            inputStream = uploadPortletRequest.getFileAsStream("file");

            String contentType = uploadPortletRequest.getContentType("file");

            FileEntry fileEntry = _taskRecordService.addTempFileEntry(groupId, folderName, sourceFileName, inputStream,
                    contentType);

        } catch (Exception e) {
            UploadException uploadException = (UploadException) actionRequest.getAttribute(WebKeys.UPLOAD_EXCEPTION);

            if (uploadException != null) {
                Throwable cause = uploadException.getCause();

                // TODO
                // if (cause instanceof FileUploadBase.IOFileUploadException) {
                // if (_log.isInfoEnabled()) {
                // _log.info("Temporary upload was cancelled");
                // }
                // }

                if (uploadException.isExceededFileSizeLimit()) {
                    throw new FileSizeException(cause);
                }

                if (uploadException.isExceededUploadRequestSizeLimit()) {
                    throw new UploadRequestSizeException(cause);
                }
            } else {
                throw e;
            }
        } finally {
            StreamUtil.cleanUp(inputStream);
        }
    }

    protected void deleteTempFileEntry(ActionRequest actionRequest, ActionResponse actionResponse, String folderName)
            throws Exception {

        _log.info("deleteTempFileEntry()");

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        JSONObject jsonObject = JSONFactoryUtil.createJSONObject();

        try {
            String fileName = ParamUtil.getString(actionRequest, "fileName");

            _taskRecordService.deleteTempFileEntry(themeDisplay.getScopeGroupId(), folderName, fileName);

            jsonObject.put("deleted", Boolean.TRUE);
        } catch (Exception e) {
            String errorMessage = themeDisplay.translate("an-unexpected-error-occurred-while-deleting-the-file");

            jsonObject.put("deleted", Boolean.FALSE);
            jsonObject.put("errorMessage", errorMessage);
        }

        JSONPortletResponseUtil.writeJSON(actionRequest, actionResponse, jsonObject);
    }

    protected void deleteTempFileEntry(long groupId, String folderName) throws PortalException {

        _log.info("deleteTempFileEntry()");

        String[] tempFileNames = _taskRecordService.getTempFileNames(groupId, folderName);

        for (String tempFileEntryName : tempFileNames) {
            _taskRecordService.deleteTempFileEntry(groupId, folderName, tempFileEntryName);
        }
    }

    @Override
    protected void doDispatch(RenderRequest renderRequest, RenderResponse renderResponse)
            throws IOException, PortletException {

        if (SessionErrors.contains(renderRequest, PrincipalException.getNestedClasses())
                || SessionErrors.contains(renderRequest, NoSuchTaskRecordException.class)) {
            include("/error.jsp", renderRequest, renderResponse);
        } else {
            super.doDispatch(renderRequest, renderResponse);
        }
    }

    protected String getEditTaskRecordURL(ActionRequest actionRequest, ActionResponse actionResponse,
            TaskRecord taskRecord) throws Exception {

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        String editTaskRecordURL = getRedirect(actionRequest, actionResponse);

        if (Validator.isNull(editTaskRecordURL)) {
            editTaskRecordURL = PortalUtil.getLayoutFullURL(themeDisplay);
        }

        String namespace = actionResponse.getNamespace();
        String windowState = actionResponse.getWindowState().toString();

        editTaskRecordURL = HttpUtil.setParameter(editTaskRecordURL, "p_p_id", PortletKeys.TIMETRACKER);
        editTaskRecordURL = HttpUtil.setParameter(editTaskRecordURL, "p_p_state", windowState);
        editTaskRecordURL = HttpUtil.setParameter(editTaskRecordURL, namespace + "mvcPath",
                templatePath + "edit_task_record.jsp");
        editTaskRecordURL = HttpUtil.setParameter(editTaskRecordURL, namespace + "redirect",
                getRedirect(actionRequest, actionResponse));
        editTaskRecordURL = HttpUtil.setParameter(editTaskRecordURL, namespace + "backURL",
                ParamUtil.getString(actionRequest, "backURL"));
        editTaskRecordURL = HttpUtil.setParameter(editTaskRecordURL, namespace + "taskRecordId",
                taskRecord.getTaskRecordId());

        return editTaskRecordURL;
    }

    /**
     * from ExportLayoutsMVCAction
     *
     */
    protected ExportImportConfiguration getExportImportConfiguration(ActionRequest actionRequest) throws Exception {

        Map<String, Serializable> exportLayoutSettingsMap = null;

        long exportImportConfigurationId = ParamUtil.getLong(actionRequest, "exportImportConfigurationId");

        if (exportImportConfigurationId > 0) {
            ExportImportConfiguration exportImportConfiguration = _exportImportConfigurationLocalService
                    .fetchExportImportConfiguration(exportImportConfigurationId);

            if (exportImportConfiguration != null) {
                exportLayoutSettingsMap = exportImportConfiguration.getSettingsMap();
            }
        }

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        boolean privateLayout = ParamUtil.getBoolean(actionRequest, "privateLayout");

        if (exportLayoutSettingsMap == null) {
            long groupId = ParamUtil.getLong(actionRequest, "liveGroupId");
            long[] layoutIds = getLayoutIds(actionRequest);

            exportLayoutSettingsMap = ExportImportConfigurationSettingsMapFactory.buildExportLayoutSettingsMap(
                    themeDisplay.getUserId(), groupId, privateLayout, layoutIds, actionRequest.getParameterMap(),
                    themeDisplay.getLocale(), themeDisplay.getTimeZone());

        }

        String taskName = ParamUtil.getString(actionRequest, "name");

        if (Validator.isNull(taskName)) {
            if (privateLayout) {
                taskName = LanguageUtil.get(actionRequest.getLocale(), "private-pages");
            } else {
                taskName = LanguageUtil.get(actionRequest.getLocale(), "public-pages");
            }
        }

        return _exportImportConfigurationLocalService.addDraftExportImportConfiguration(themeDisplay.getUserId(),
                taskName, ExportImportConfigurationConstants.TYPE_EXPORT_LAYOUT, exportLayoutSettingsMap);
    }

    /**
     * from ExportLayoutsMVCAction
     *
     */
    protected long[] getLayoutIds(PortletRequest portletRequest) throws Exception {

        Set<Layout> layouts = new LinkedHashSet<>();

        Map<Long, Boolean> layoutIdMap = ExportImportHelperUtil.getLayoutIdMap(portletRequest);

        for (Map.Entry<Long, Boolean> entry : layoutIdMap.entrySet()) {
            long plid = GetterUtil.getLong(String.valueOf(entry.getKey()));
            boolean includeChildren = entry.getValue();

            Layout layout = _layoutLocalService.getLayout(plid);

            if (!layouts.contains(layout)) {
                layouts.add(layout);
            }

            if (includeChildren) {
                layouts.addAll(layout.getAllChildren());
            }
        }

        return ExportImportHelperUtil.getLayoutIds(new ArrayList<Layout>(layouts));
    }

    protected void getTaskRecord(PortletRequest portletRequest) throws Exception {

        long taskRecordId = ParamUtil.getLong(portletRequest, "taskRecordId");

        if (taskRecordId <= 0) {
            return;
        }

        TaskRecord taskRecord = _taskRecordService.getTaskRecord(taskRecordId);

        portletRequest.setAttribute(TimetrackerWebKeys.TASK_RECORD, taskRecord);
    }

    protected void importData(ActionRequest actionRequest, String folderName) throws Exception {

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        long groupId = ParamUtil.getLong(actionRequest, "groupId");

        FileEntry fileEntry = ExportImportHelperUtil.getTempFileEntry(groupId, themeDisplay.getUserId(), folderName);

        InputStream inputStream = null;

        try {
            inputStream = _dlFileEntryLocalService.getFileAsStream(fileEntry.getFileEntryId(), fileEntry.getVersion(),
                    false);

            importData(actionRequest, fileEntry.getTitle(), inputStream);

            deleteTempFileEntry(groupId, folderName);

        } finally {
            StreamUtil.cleanUp(inputStream);
        }
    }

    protected void importData(ActionRequest actionRequest, String fileName, InputStream inputStream) throws Exception {

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        long groupId = ParamUtil.getLong(actionRequest, "groupId");
        boolean privateLayout = ParamUtil.getBoolean(actionRequest, "privateLayout");

        _log.info("groupId = " + groupId);

        // ExportImportConfiguration taskRecordConfiguration =
        // ExportImportConfigurationFactory
        // .buildDefaultLocalPublishingExportImportConfiguration(actionRequest);
        //
        // taskRecordConfiguration.setGroupId(groupId);

        _taskRecordService.importTaskRecordsInBackground(inputStream);

    }

    @Reference
    protected void setDLFileEntryLocalService(DLFileEntryLocalService dlFileEntryLocalService) {
        this._dlFileEntryLocalService = dlFileEntryLocalService;
    }

    @Reference(unbind = "-")
    protected void setExportImportConfigurationLocalService(
            ExportImportConfigurationLocalService exportImportConfigurationLocalService) {

        _exportImportConfigurationLocalService = exportImportConfigurationLocalService;
    }

    @Reference(unbind = "-")
    protected void setLayoutLocalService(LayoutLocalService layoutLocalService) {

        _layoutLocalService = layoutLocalService;
    }

    @Reference
    protected void setTaskRecordService(TaskRecordService taskRecordService) {
        this._taskRecordService = taskRecordService;
    }

    private DLFileEntryLocalService _dlFileEntryLocalService;

    private LayoutLocalService _layoutLocalService;

    private ExportImportConfigurationLocalService _exportImportConfigurationLocalService;

    private TaskRecordService _taskRecordService;

    private volatile TimetrackerConfiguration _timetrackerConfiguration;

    private static final String REQUEST_PROCESSED = "request_processed";

    private static final Log _log = LogFactoryUtil.getLog(TimetrackerPortlet.class.getName());

}
