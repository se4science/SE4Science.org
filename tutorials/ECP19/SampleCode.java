/// <summary>
///The customer reached us because their custom code (below) was performing very poorly. The client actually
///reported that a single document insert action takes anywhere from 6 to 10 seconds. You would probably agree
///there must be something wrong with the code as 10 seconds for a simple insert is just too much. And you are
///right - it is very suspicious whenever single action takes so long.
///The custom code below imports a new content into Kentico CMS using our API. Source data are
///retrieved through the channel to external web service. Perhaps the performance loss can be caused by the
///delay in the response coming from the web service. That's true; you cannot really rely on external service.
///There is no guarantee service would be available all the time and there will always be the factor of handshake
///and request-response cost. Anyway, for this particular case let's assume the delay is no longer than 1 to
///3 seconds. We are therefore still looking at additional 7-9 seconds of the execution time lost somewhere
///along the lines of our custom code.
///Let's take a closer look on the code now.
/// </summary>

namespace CUSTOM.Business{

public static class LocationManager {
    private static LocationManagerDataSet _lmds;
    private static BranchManagerDataSet _bmds;
    private static AgeRangeDataSet _agds;
    private static WebTimeCrossReferenceManagerDataSet _wtcds;
    private static WebTimeSFYPManagerDataSet _wtsds;
    private static WebTimeItemCrossReferenceManagerDataSet _wtixds;
    private static JobScheduleDataSet _jsds;

    private static DataSet ClassesDataSet;
    private static string durationstr;
    private static string currentSite;
    private static string currentCulture;

    public static string CurrentSite
    {
            get
            {
                if (string.IsNullOrEmpty(currentSite))
                {
                    if (CMS.CMSHelper.CMSContext.CurrentSiteName == string.Empty)
                        currentSite = ConfigurationManager.AppSettings["DefaultSite"].ToString();
                    else
                        currentSite = CMS.CMSHelper.CMSContext.CurrentSiteName;
                }
                return currentSite;
            }
        }

    public static string CurrentCulture
    {
            get
            {
                if (string.IsNullOrEmpty(currentCulture))
                {
                    if (CMS.CMSHelper.CMSContext.CurrentSiteName == string.Empty)
                        currentCulture = ConfigurationManager.AppSettings["DefaultCulture"].ToString();
                    else
                        currentCulture = CMS.CMSHelper.CMSContext.CurrentDocumentCulture.CultureCode;
                }
                return currentCulture;
            }
        }

    public static LocationManagerDataSet GetAllLocations() {
        _lmds = SqlDataClient.GetAllLocations();
        return _lmds;
    }

    public static JobScheduleDataSet GetAllJobBranches() {
        _jsds = SqlDataClient.GetAllJobBranches();
        return _jsds;
    }

    public static void UpdateJobBranch(int locationID) {
        SqlDataClient.UpdateJobBranch(locationID);
    }

    public static AgeRangeDataSet GetAllAgeRange() {
        _agds = SqlDataClient.GetAllAgeRange();
        return _agds;
    }

    public static LocationManagerDataSet GetLocation(int LocationID) {
        _lmds = SqlDataClient.GetLocation(LocationID);
        return _lmds;
    }

    public static BranchManagerDataSet GetBranchByBranchName(string branchName) {
        _bmds = SqlDataClient.GetBranchByBranchName(branchName);
        return _bmds;
    }

    public static LocationManagerDataSet GetLocationDataSet(int LocationID) {
        _lmds = SqlDataClient.GetLocation(LocationID);
        return _lmds;
    }

    public static BranchManagerDataSet GetValidWebTimeBranches() {
        _bmds = SqlDataClient.GetValidBranches();
        return _bmds;
    }

    public static void GetWebItemItemCrossReferenceByLocationID(int locationID) {
        _wtixds = SqlDataClient.GetWebItemItemCrossReferenceByLocationID(locationID);
    }

    public static void GetWebTimeCrossReferenceByLocationID(int locationID) {
        _wtcds = SqlDataClient.GetWebTimeCrossReferenceByLocationID(locationID);
    }

    public static void GetWebTimeSFYPDataByLocationID(int locationID) {
        _wtsds = SqlDataClient.GetWebTimeSFYPDataByLocationID(locationID);
    }

    public static string RefreshDataFromWebService(int locationID)
        {
            DateTime startTime = DateTime.Now;
            GetLocation(locationID);
 
            Guid batchNumber = Guid.NewGuid();
 
            foreach (LocationManagerDataSet.LocationDataTableRow drow in _lmds.LocationDataTable.Rows)
            {
                // Create the web request   
                HttpWebRequest request = WebRequest.Create(string.Format("http://www.somedomain.com", drow.WebserviceLocationID)) as HttpWebRequest;
                
                if (request != null)
                {
                    // Get response   
                    using (HttpWebResponse response = request.GetResponse() as HttpWebResponse)
                    {
                        if (response != null)
                        {
                            string contents = string.Empty;
                            // Load data into a dataset   
                            DataSet dsw = new DataSet();
                            using (StreamReader reader = new StreamReader(response.GetResponseStream()))
                            {
                                contents = reader.ReadToEnd().Replace("& UP", "+");
                            }
 
                            System.IO.StringReader strcontents = new System.IO.StringReader(contents);
                            WebTimeDataManagerDataSet dsWeather = new WebTimeDataManagerDataSet();
                            dsw.ReadXml(strcontents);
 
                            if (dsw.Tables["Transaction"].Rows[0]["TRAN_STATUS"].ToString() != "FAIL")
                            {
                                // save the data
                                SqlDataClient.SaveWebTimeData(dsw, drow.WebtimeLocationID, batchNumber);
                            }
                        }
                    }
                }
            }
 
            DateTime stopTime = DateTime.Now;
            TimeSpan duration = stopTime - startTime;
            durationstr = duration.ToString();
            return ("RefreshTimes: " + durationstr);
        }

    public static string PopulateNodes(int locationID)
        {
            durationstr = string.Empty;
 
            GetLocation(locationID);
            GetValidWebTimeBranches();
 
            foreach (LocationManagerDataSet.LocationDataTableRow drow in _lmds.LocationDataTable.Rows)
            {
                DeleteSchedules(drow.WebtimeLocationID, _bmds);
                AddNewSchedules(drow.WebtimeLocationID, _bmds);
            }
 
            return (durationstr);
        }

    private static void DeleteSchedules(int locationID, BranchManagerDataSet bm)
        {
            DateTime startTime = DateTime.Now;
            try
            {
                // look for the Programs Nodes
                DataRow[] drow = bm.something_Branch.Select("WebtimeLocationID=" + locationID.ToString());
                string programpath = string.Empty;
                string unprocessedpath = string.Empty;
 
                if (drow.Count() > 0)
                {
                    programpath = ((BranchManagerDataSet.something_BranchRow)drow[0]).NodeAliasPath.ToString() + "/Programs/%";
                }
 
                // if there is a real program path, go to the path using the Kentico API
                if (programpath != string.Empty)
                {
                    // create a TreeProvider instance
                    UserInfo ui = UserInfoProvider.GetUserInfo("administrator");
                    CMS.TreeEngine.TreeProvider tree = new CMS.TreeEngine.TreeProvider(ui);
 
                    DataSet treeDS = tree.SelectNodes(CurrentSite, programpath, CurrentCulture, true, "something.ClassTime");
                    if (treeDS != null && treeDS.Tables.Count > 0)
                    {
                        CMS.CMSHelper.CMSContext.Init();
                        DataTable dtable = treeDS.Tables[0];
                        foreach (DataRow dr in dtable.Rows)
                        {
                            // Get document with specified site, aliaspath and culture
 
                            CMS.TreeEngine.TreeNode node = tree.SelectSingleNode(CurrentSite, dr["NodeAliaspath"].ToString(), CurrentCulture, true, "something.ClassTime");
                            if (node != null)
                            {
                                // Delete the document
                                DocumentHelper.DeleteDocument(node, tree, true, true, true);
                                node.Delete();
                            }
                        }
                    }
 
                    // delete also from the Unprocessed Bucket
                    unprocessedpath = "/Unprocessed" + ((BranchManagerDataSet.something_BranchRow)drow[0]).NodeAliasPath + "/%";
                    DataSet unprocessedDS = new DataSet();
 
                    unprocessedDS = tree.SelectNodes(CurrentSite, unprocessedpath, CurrentCulture, true, "something.ClassTime");
                    if (unprocessedDS != null && unprocessedDS.Tables.Count > 0)
                    {
                        // this is needed for the windows service routine because it is not in context of the website
                        CMS.CMSHelper.CMSContext.Init();
                        DataTable unprocesseddtable = unprocessedDS.Tables[0];
                        foreach (DataRow dr in unprocesseddtable.Rows)
                        {
                            // Get document with specified site, aliaspath and culture
 
                            CMS.TreeEngine.TreeNode node = tree.SelectSingleNode(CurrentSite, dr["NodeAliaspath"].ToString(), CurrentCulture, true, "something.ClassTime");
                            if (node != null)
                            {
                                // Delete the document
                                DocumentHelper.DeleteDocument(node, tree, true, true, true);
                                node.Delete();
                            }
                        }
                    }
                }
 
                DateTime stopTime = DateTime.Now;
                TimeSpan duration = stopTime - startTime;
                durationstr = "Delete Times: " + duration.ToString();
            }
            catch (Exception ex)
            {
                throw new Exception(ex.Message);
            }
        }

    public static CMSTreeNodeOfferingCategoryManagerDataSet GetOfferingCategoryNodesByBranchPath(
            string branchnamepath) {
        CMSTreeNodeOfferingCategoryManagerDataSet currentProgramsDS = new CMSTreeNodeOfferingCategoryManagerDataSet();

        UserInfo ui = UserInfoProvider.GetUserInfo("administrator");
        CMS.TreeEngine.TreeProvider tree = new CMS.TreeEngine.TreeProvider(ui);

        currentProgramsDS.CMSTreeNodeOfferingCategory.TableName = "something.OfferingCategoryPage";

        string programpath = branchnamepath + "/Programs/%";

        // get all the program offerings for this program path
        currentProgramsDS.Merge(
                tree.SelectNodes(CurrentSite, programpath, CurrentCulture, true, "something.OfferingCategoryPage"));

        return currentProgramsDS;
    }

    public static CMSTreeNodeOfferingCategoryManagerDataSet GetOfferingCategoryNodes(string branchpath) {
        CMSTreeNodeOfferingCategoryManagerDataSet currentProgramsDS = new CMSTreeNodeOfferingCategoryManagerDataSet();

        UserInfo ui = UserInfoProvider.GetUserInfo("administrator");
        CMS.TreeEngine.TreeProvider tree = new CMS.TreeEngine.TreeProvider(ui);

        currentProgramsDS.CMSTreeNodeOfferingCategory.TableName = "something.OfferingCategoryPage";

        string programpath = branchpath + "/Programs/%";

        // get all the program offerings for this program path
        currentProgramsDS.Merge(
                tree.SelectNodes(CurrentSite, programpath, CurrentCulture, true, "something.OfferingCategoryPage"));

        return currentProgramsDS;
    }

    private static void AddNewSchedules(int locationID, BranchManagerDataSet bm)
        {
            // look for the Programs Nodes
            DataRow[] drow = bm.something_Branch.Select("WebtimeLocationID=" + locationID.ToString());
            string programpath = string.Empty;
 
            DateTime startTime = DateTime.Now;
 
            try
            {
                if (drow.Count() > 0)
                {
                    programpath = ((BranchManagerDataSet.something_BranchRow)drow[0]).NodeAliasPath + "/Programs/%";
 
                    // Get the Import Data
                    GetWebTimeSFYPDataByLocationID(locationID);
                    GetWebItemItemCrossReferenceByLocationID(locationID);
 
                    UserInfo ui = UserInfoProvider.GetUserInfo("administrator");
                    CMS.TreeEngine.TreeProvider tree = new CMS.TreeEngine.TreeProvider(ui);
 
                    CMSTreeNodeClassTypeDataSet ClassesDS = new CMSTreeNodeClassTypeDataSet();
                    ClassesDS.CMSTreeNodeClassType.TableName = "something.Class";
 
                    // get all the classes for this program path
 
                    DataSet ds = tree.SelectNodes(CurrentSite, programpath, CurrentCulture, true, "something.Class");
                    ClassesDS.Merge(ds);
 
                    //Read the Import Data First
                    foreach (WebTimeSFYPManagerDataSet.SOMETHING_WebTimeSFYPDataTableRow dsrow in _wtsds.SOMETHING_WebTimeSFYPDataTable.Rows)
                    {                        
                        // locate for the pgm code in the crossreference file
                        string pgmcodetosearch = string.Empty;
 
                        // check if the last 2 are numbers
                        if (dsrow.THRUSTCODE != string.Empty)
                        {
                            // create exception
                            // look for the Programs Nodes
                            string selectstring = "WebTime_ThrustCode='" + dsrow.THRUSTCODE.ToString() + "'";
                            DataRow[] xrows = ClassesDS.Tables[0].Select(selectstring);
 
                            if (xrows.Count() > 0)
                            {
                                // get parent node for new document
                                CreateNode(((CMSTreeNodeClassTypeDataSet.CMSTreeNodeClassTypeRow)xrows[0]).NodeAliasPath, tree, dsrow, ((BranchManagerDataSet.something_BranchRow)drow[0]).WebserviceLocationID.ToString());
                            }
                            else
                            {
                                // create exception
                                // look for the Programs Nodes
                                string parentpath = string.Empty;
 
                                if (drow.Count() > 0)
                                {
                                    parentpath = "/Unprocessed" + ((BranchManagerDataSet.something_BranchRow)drow[0]).NodeAliasPath;
                                }
 
                                CreateNode(parentpath, tree, dsrow, ((BranchManagerDataSet.something_BranchRow)drow[0]).WebserviceLocationID.ToString());
                            }
                        }
                        else
                        {
                            // create exception
                            // look for the Programs Nodes
                            string parentpath = string.Empty;
 
                            if (drow.Count() > 0)
                            {
                                parentpath = "/Unprocessed" + ((BranchManagerDataSet.something_BranchRow)drow[0]).NodeAliasPath;
                            }
 
                            CreateNode(parentpath, tree, dsrow, ((BranchManagerDataSet.something_BranchRow)drow[0]).WebserviceLocationID.ToString());
                        }
                    }
                }
 
                DateTime stopTime = DateTime.Now;
                TimeSpan duration = stopTime - startTime;
 
                durationstr = durationstr + "<br/>" + "Add Times: " + duration.ToString() + "<br>";
            }
            catch (Exception ex)
            {
                throw new Exception(ex.Message);
            }
        }

    private static void CreateNode(string parentpath, CMS.TreeEngine.TreeProvider tree,
            WebTimeSFYPManagerDataSet.SOMETHING_WebTimeSFYPDataTableRow dsrow, string webservicelocationID) {
        // get parent node for new document

        // this is needed for the windows service because it is not in the context of
        // the website
        CMS.CMSHelper.CMSContext.Init();
        CMS.TreeEngine.TreeNode parentClassNode = tree.SelectSingleNode(CurrentSite, parentpath, CurrentCulture);

        // create a new tree node
        CMS.TreeEngine.TreeNode node = new CMS.TreeEngine.TreeNode("something.ClassTime", tree);

        // check off the properties
        if (parentClassNode != null) {
            // set document properties
            string nodename = GenerateNodeName(dsrow);
            if (nodename == string.Empty)
                nodename = dsrow.KEY;

            node.NodeName = nodename;
            node.NodeAlias = nodename;

            node.SetValue("DocumentCulture", CurrentCulture);
            node.SetValue("DayOfWeek", nodename);
            node.SetValue("DocumentName", nodename);

            string startdate = string.Empty;
            string enddate = string.Empty;

            if (dsrow.BEGIN_DATE != string.Empty) {
                startdate = DateTime.Now.Year.ToString().Substring(0, 2) + dsrow.BEGIN_DATE;
                node.SetValue("StartDate", Convert.ToDateTime(
                        startdate.Substring(0, 4) + "/" + startdate.Substring(4, 2) + "/" + startdate.Substring(6, 2)));
            }

            if (dsrow.END_DATE != string.Empty) {
                enddate = DateTime.Now.Year.ToString().Substring(0, 2) + dsrow.END_DATE;
                node.SetValue("EndDate", Convert.ToDateTime(
                        enddate.Substring(0, 4) + "/" + enddate.Substring(4, 2) + "/" + enddate.Substring(6, 2)));
            }

            node.SetValue("StartTime", dsrow.START_TIME);
            node.SetValue("EndTime", dsrow.END_TIME);
            node.Insert(parentClassNode.NodeID);

            WorkflowManager wm = new WorkflowManager(tree);

            // check if the node supports workflow
            WorkflowInfo wi = wm.GetNodeWorkflow(node);
            if (wi != null) {
                // Approve until the step is publish
                WorkflowStepInfo currentStep = wm.GetStepInfo(node);
                if (wm != null) {
                    while ((currentStep != null) && (currentStep.StepName.ToLower() != "published")) {
                        currentStep = wm.MoveToNextStep(node, "");
                    }
                }
            }
        }
    }
}}