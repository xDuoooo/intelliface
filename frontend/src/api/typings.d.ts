declare namespace API {
  type BaseResponseBoolean_ = {
    code?: number;
    data?: boolean;
    message?: string;
  };

  type BaseResponseMapStringObject_ = {
    code?: number;
    data?: Record<string, any>;
    message?: string;
  };

  type BaseResponseLearningGoalData_ = {
    code?: number;
    data?: LearningGoalData;
    message?: string;
  };

  type BaseResponseListPostVO_ = {
    code?: number;
    data?: PostVO[];
    message?: string;
  };

  type BaseResponseInt_ = {
    code?: number;
    data?: number;
    message?: string;
  };

  type BaseResponseListInt_ = {
    code?: number;
    data?: number[];
    message?: string;
  };

  type BaseResponseLoginUserVO_ = {
    code?: number;
    data?: LoginUserVO;
    message?: string;
  };

  type BaseResponseLong_ = {
    code?: number;
    data?: number;
    message?: string;
  };

  type BaseResponsePostSubmitResultVO_ = {
    code?: number;
    data?: PostSubmitResultVO;
    message?: string;
  };

  type BaseResponseListQuestionVO_ = {
    code?: number;
    data?: QuestionVO[];
    message?: string;
  };

  type BaseResponseMockInterview_ = {
    code?: number;
    data?: MockInterview;
    message?: string;
  };

  type BaseResponsePageMockInterview_ = {
    code?: number;
    data?: PageMockInterview_;
    message?: string;
  };

  type BaseResponsePagePost_ = {
    code?: number;
    data?: PagePost_;
    message?: string;
  };

  type BaseResponsePagePostVO_ = {
    code?: number;
    data?: PagePostVO_;
    message?: string;
  };

  type BaseResponsePagePostReportVO_ = {
    code?: number;
    data?: PagePostReportVO_;
    message?: string;
  };

  type BaseResponsePageQuestion_ = {
    code?: number;
    data?: PageQuestion_;
    message?: string;
  };

  type BaseResponsePageQuestionBank_ = {
    code?: number;
    data?: PageQuestionBank_;
    message?: string;
  };

  type BaseResponsePageQuestionBankQuestion_ = {
    code?: number;
    data?: PageQuestionBankQuestion_;
    message?: string;
  };

  type BaseResponsePageQuestionBankQuestionVO_ = {
    code?: number;
    data?: PageQuestionBankQuestionVO_;
    message?: string;
  };

  type BaseResponsePageQuestionBankVO_ = {
    code?: number;
    data?: PageQuestionBankVO_;
    message?: string;
  };

  type BaseResponsePageQuestionVO_ = {
    code?: number;
    data?: PageQuestionVO_;
    message?: string;
  };

  type BaseResponseGlobalLeaderboardVO_ = {
    code?: number;
    data?: GlobalLeaderboardVO;
    message?: string;
  };

  type BaseResponsePageUser_ = {
    code?: number;
    data?: PageUser_;
    message?: string;
  };

  type BaseResponsePageUserVO_ = {
    code?: number;
    data?: PageUserVO_;
    message?: string;
  };

  type BaseResponsePostVO_ = {
    code?: number;
    data?: PostVO;
    message?: string;
  };

  type BaseResponsePageSecurityAlert_ = {
    code?: number;
    data?: PageSecurityAlert_;
    message?: string;
  };

  type BaseResponseQuestionBankQuestionVO_ = {
    code?: number;
    data?: QuestionBankQuestionVO;
    message?: string;
  };

  type BaseResponseQuestionBankVO_ = {
    code?: number;
    data?: QuestionBankVO;
    message?: string;
  };

  type BaseResponseQuestionVO_ = {
    code?: number;
    data?: QuestionVO;
    message?: string;
  };

  type BaseResponseQuestionAnswerEvaluateVO_ = {
    code?: number;
    data?: QuestionAnswerEvaluateVO;
    message?: string;
  };

  type BaseResponseQuestionBankLeaderboardVO_ = {
    code?: number;
    data?: QuestionBankLeaderboardVO;
    message?: string;
  };

  type BaseResponseResumeQuestionRecommendVO_ = {
    code?: number;
    data?: ResumeQuestionRecommendVO;
    message?: string;
  };

  type BaseResponseString_ = {
    code?: number;
    data?: string;
    message?: string;
  };

  type BaseResponseSystemConfigVO_ = {
    code?: number;
    data?: SystemConfigVO;
    message?: string;
  };

  type BaseResponseUser_ = {
    code?: number;
    data?: User;
    message?: string;
  };

  type BaseResponseUserVO_ = {
    code?: number;
    data?: UserVO;
    message?: string;
  };

  type BaseResponseUserProfileVO_ = {
    code?: number;
    data?: UserProfileVO;
    message?: string;
  };

  type SystemConfigUpdateRequest = {
    allowRegister?: boolean;
    allowGuestViewPost?: boolean;
    allowGuestViewQuestion?: boolean;
    announcement?: string;
    enableEmailNotification?: boolean;
    enableLearningGoalReminder?: boolean;
    enableSiteNotification?: boolean;
    maintenanceMode?: boolean;
    requireCaptcha?: boolean;
    seoKeywords?: string;
    siteName?: string;
  };

  type SystemConfigVO = {
    allowRegister?: boolean;
    allowGuestViewPost?: boolean;
    allowGuestViewQuestion?: boolean;
    announcement?: string;
    createTime?: string;
    enableEmailNotification?: boolean;
    enableLearningGoalReminder?: boolean;
    enableSiteNotification?: boolean;
    id?: string | number;
    maintenanceMode?: boolean;
    requireCaptcha?: boolean;
    seoKeywords?: string;
    siteName?: string;
    updateTime?: string;
  };

  type BaseResponsePageUserQuestionHistoryVO_ = {
    code?: number;
    data?: PageUserQuestionHistoryVO_;
    message?: string;
  };

  type BaseResponseListMapStringObject_ = {
    code?: number;
    data?: Record<string, any>[];
    message?: string;
  };

  type BaseResponseNotificationVO_ = {
    code?: number;
    data?: NotificationVO;
    message?: string;
  };

  type BaseResponsePageNotificationVO_ = {
    code?: number;
    data?: PageNotificationVO_;
    message?: string;
  };

  type checkUsingGETParams = {
    /** echostr */
    echostr?: string;
    /** nonce */
    nonce?: string;
    /** signature */
    signature?: string;
    /** timestamp */
    timestamp?: string;
  };

  type DeleteRequest = {
    id?: string | number;
  };

  type doLoginUsingDELETEParams = {
    /** password */
    password?: string;
    /** username */
    username?: string;
  };

  type doLoginUsingGETParams = {
    /** password */
    password?: string;
    /** username */
    username?: string;
  };

  type doLoginUsingPATCHParams = {
    /** password */
    password?: string;
    /** username */
    username?: string;
  };

  type doLoginUsingPOSTParams = {
    /** password */
    password?: string;
    /** username */
    username?: string;
  };

  type doLoginUsingPUTParams = {
    /** password */
    password?: string;
    /** username */
    username?: string;
  };

  type getMockInterviewByIdUsingGETParams = {
    /** id */
    id?: string | number;
  };

  type getPostVOByIdUsingGETParams = {
    /** id */
    id?: string | number;
  };

  type getQuestionBankQuestionVOByIdUsingGETParams = {
    /** id */
    id?: string | number;
  };

  type getQuestionBankVOByIdUsingGETParams = {
    current?: number;
    description?: string;
    id?: string | number;
    needQueryQuestionList?: boolean;
    notId?: string | number;
    pageSize?: number;
    picture?: string;
    searchText?: string;
    sortField?: string;
    sortOrder?: string;
    title?: string;
    userId?: string | number;
  };

  type getQuestionVOByIdUsingGETParams = {
    /** id */
    id?: string | number;
  };

  type getUserByIdUsingGETParams = {
    /** id */
    id?: string | number;
  };

  type getUserSignInRecordUsingGETParams = {
    /** year */
    year?: number;
  };

  type getUserVOByIdUsingGETParams = {
    /** id */
    id?: string | number;
  };

  type getUserProfileVOByIdUsingGETParams = {
    /** id */
    id?: string | number;
  };

  type LoginUserVO = {
    careerDirection?: string;
    city?: string;
    createTime?: string;
    email?: string;
    id?: string | number;
    interestTagList?: string[];
    profileVisibleFieldList?: string[];
    passwordConfigured?: number;
    phone?: string;
    updateTime?: string;
    userAccount?: string;
    userAvatar?: string;
    userName?: string;
    userProfile?: string;
    userRole?: string;
    mpOpenId?: string;
    githubId?: string;
    giteeId?: string;
    googleId?: string;
  };

  type LeaderboardUserVO = {
    metricText?: string;
    metricValue?: number;
    rank?: number;
    userAvatar?: string;
    userId?: string | number;
    userName?: string;
    userRole?: string;
  };

  type LearningGoalData = {
    dailyTarget?: number;
    reminderEnabled?: boolean;
    lastReminderTime?: string;
  };

  type LeaderboardBoardVO = {
    currentUserItem?: LeaderboardUserVO;
    description?: string;
    key?: string;
    metricLabel?: string;
    rankingList?: LeaderboardUserVO[];
    title?: string;
  };

  type GlobalLeaderboardVO = {
    boardList?: LeaderboardBoardVO[];
  };

  type MockInterview = {
    createTime?: string;
    difficulty?: string;
    currentRound?: number;
    expectedRounds?: number;
    id?: string | number;
    interviewType?: string;
    isDelete?: number;
    jobPosition?: string;
    messages?: string;
    report?: string;
    status?: number;
    techStack?: string;
    updateTime?: string;
    userId?: string | number;
    workExperience?: string;
    resumeText?: string;
  };

  type MockInterviewAddRequest = {
    difficulty?: string;
    expectedRounds?: number;
    interviewType?: string;
    jobPosition?: string;
    resumeText?: string;
    techStack?: string;
    workExperience?: string;
  };

  type MockInterviewEventRequest = {
    event?: string;
    id?: string | number;
    message?: string;
  };

  type MockInterviewQueryRequest = {
    current?: number;
    difficulty?: string;
    id?: string | number;
    interviewType?: string;
    jobPosition?: string;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
    status?: number;
    techStack?: string;
    userId?: string | number;
    workExperience?: string;
  };

  type UserQuestionStudySessionReportRequest = {
    durationSeconds?: number;
    questionId?: string | number;
  };

  type OrderItem = {
    asc?: boolean;
    column?: string;
  };

  type PageMockInterview_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: MockInterview[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PagePost_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: Post[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PagePostVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: PostVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PagePostReportVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: PostReportVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageQuestion_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: Question[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageQuestionBank_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: QuestionBank[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageQuestionBankQuestion_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: QuestionBankQuestion[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageQuestionBankQuestionVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: QuestionBankQuestionVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageQuestionBankVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: QuestionBankVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageQuestionVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: QuestionVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageUser_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: User[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageUserVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: UserVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageUserQuestionHistoryVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: UserQuestionHistoryVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageNotificationVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: NotificationVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type PageSecurityAlert_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: SecurityAlert[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type Post = {
    content?: string;
    createTime?: string;
    favourNum?: number;
    id?: string | number;
    isFeatured?: number;
    isDelete?: number;
    isTop?: number;
    reportNum?: number;
    reviewMessage?: string;
    reviewStatus?: number;
    reviewTime?: string;
    reviewUserId?: string | number;
    tags?: string;
    thumbNum?: number;
    title?: string;
    updateTime?: string;
    userId?: string | number;
  };

  type PostAddRequest = {
    content?: string;
    tags?: string[];
    title?: string;
  };

  type PostEditRequest = {
    content?: string;
    id?: string | number;
    tags?: string[];
    title?: string;
  };

  type PostFavourAddRequest = {
    postId?: string | number;
  };

  type PostFavourQueryRequest = {
    current?: number;
    pageSize?: number;
    postQueryRequest?: PostQueryRequest;
    sortField?: string;
    sortOrder?: string;
    userId?: string | number;
  };

  type PostOperateRequest = {
    id?: string | number;
    isFeatured?: number;
    isTop?: number;
  };

  type PostQueryRequest = {
    content?: string;
    current?: number;
    favourUserId?: string | number;
    id?: string | number;
    isFeatured?: number;
    isTop?: number;
    notId?: string | number;
    orTags?: string[];
    pageSize?: number;
    reviewStatus?: number;
    searchText?: string;
    sortField?: string;
    sortOrder?: string;
    tags?: string[];
    title?: string;
    userId?: string | number;
  };

  type PostReviewRequest = {
    id?: string | number;
    reviewMessage?: string;
    reviewStatus?: number;
  };

  type PostReportRequest = {
    postId?: string | number;
    reason?: string;
  };

  type PostReportQueryRequest = {
    current?: number;
    pageSize?: number;
    postId?: string | number;
    sortField?: string;
    sortOrder?: string;
    status?: number;
  };

  type PostReportProcessRequest = {
    id?: string | number;
    status?: number;
  };

  type PostThumbAddRequest = {
    postId?: string | number;
  };

  type PostUpdateRequest = {
    content?: string;
    id?: string | number;
    tags?: string[];
    title?: string;
  };

  type PostVO = {
    content?: string;
    createTime?: string;
    favourNum?: number;
    hasFavour?: boolean;
    hasThumb?: boolean;
    id?: string | number;
    ipLocation?: string;
    isFeatured?: number;
    isTop?: number;
    reportNum?: number;
    reviewMessage?: string;
    reviewStatus?: number;
    reviewTime?: string;
    tagList?: string[];
    thumbNum?: number;
    title?: string;
    updateTime?: string;
    user?: UserVO;
    userId?: string | number;
  };

  type PostReportVO = {
    createTime?: string;
    id?: string | number;
    postId?: string | number;
    reason?: string;
    reporter?: UserVO;
    status?: number;
    userId?: string | number;
  };

  type PostSubmitResultVO = {
    id?: string | number;
    reviewMessage?: string;
    reviewStatus?: number;
  };

  type Question = {
    answer?: string;
    content?: string;
    createTime?: string;
    difficulty?: string;
    editTime?: string;
    id?: string | number;
    isDelete?: number;
    reviewMessage?: string;
    reviewStatus?: number;
    reviewTime?: string;
    reviewUserId?: string | number;
    tags?: string;
    title?: string;
    updateTime?: string;
    userId?: string | number;
  };

  type QuestionAddRequest = {
    answer?: string;
    content?: string;
    difficulty?: string;
    tags?: string[];
    title?: string;
  };

  type QuestionAnswerEvaluateRequest = {
    answerContent?: string;
    questionId?: string | number;
  };

  type QuestionAnswerEvaluateVO = {
    analysisSource?: string;
    followUpQuestionList?: string[];
    improvementList?: string[];
    level?: string;
    missedPointList?: string[];
    referenceSuggestion?: string;
    score?: number;
    strengthList?: string[];
    summary?: string;
    transcript?: string;
  };

  type QuestionAIGenerateRequest = {
    number?: number;
    questionType?: string;
  };

  type QuestionBank = {
    createTime?: string;
    description?: string;
    editTime?: string;
    id?: string | number;
    isDelete?: number;
    picture?: string;
    reviewMessage?: string;
    reviewStatus?: number;
    reviewTime?: string;
    reviewUserId?: string | number;
    title?: string;
    updateTime?: string;
    userId?: string | number;
  };

  type QuestionBankAddRequest = {
    description?: string;
    picture?: string;
    title?: string;
  };

  type QuestionBankEditRequest = {
    description?: string;
    id?: string | number;
    picture?: string;
    title?: string;
  };

  type QuestionBankQueryRequest = {
    current?: number;
    description?: string;
    id?: string | number;
    needQueryQuestionList?: boolean;
    notId?: string | number;
    pageSize?: number;
    picture?: string;
    reviewStatus?: number;
    searchText?: string;
    sortField?: string;
    sortOrder?: string;
    title?: string;
    userId?: string | number;
  };

  type QuestionBankReviewRequest = {
    id?: string | number;
    reviewMessage?: string;
    reviewStatus?: number;
  };

  type QuestionBankSubmitReviewRequest = {
    id?: string | number;
  };

  type QuestionBankQuestion = {
    createTime?: string;
    id?: string | number;
    questionBankId?: string | number;
    questionId?: string | number;
    updateTime?: string;
    userId?: string | number;
  };

  type QuestionBankQuestionAddRequest = {
    questionBankId?: string | number;
    questionId?: string | number;
  };

  type QuestionBankQuestionBatchAddRequest = {
    questionBankId?: string | number;
    questionIdList?: Array<string | number>;
  };

  type QuestionBankQuestionBatchRemoveRequest = {
    questionBankId?: string | number;
    questionIdList?: Array<string | number>;
  };

  type QuestionBankQuestionQueryRequest = {
    current?: number;
    id?: string | number;
    notId?: string | number;
    pageSize?: number;
    questionBankId?: string | number;
    questionId?: string | number;
    sortField?: string;
    sortOrder?: string;
    userId?: string | number;
  };

  type QuestionBankQuestionRemoveRequest = {
    questionBankId?: string | number;
    questionId?: string | number;
  };

  type QuestionBankQuestionUpdateRequest = {
    id?: string | number;
    questionBankId?: string | number;
    questionId?: string | number;
  };

  type QuestionBankQuestionVO = {
    createTime?: string;
    id?: string | number;
    questionBankId?: string | number;
    questionId?: string | number;
    tagList?: string[];
    updateTime?: string;
    user?: UserVO;
    userId?: string | number;
  };

  type QuestionBankUpdateRequest = {
    description?: string;
    id?: string | number;
    picture?: string;
    title?: string;
  };

  type QuestionBankVO = {
    createTime?: string;
    description?: string;
    id?: string | number;
    picture?: string;
    questionPage?: PageQuestionVO_;
    reviewMessage?: string;
    reviewStatus?: number;
    reviewTime?: string;
    reviewUserId?: string | number;
    title?: string;
    updateTime?: string;
    user?: UserVO;
    userId?: string | number;
  };

  type QuestionBatchDeleteRequest = {
    questionIdList?: Array<string | number>;
  };

  type QuestionEditRequest = {
    answer?: string;
    content?: string;
    difficulty?: string;
    id?: string | number;
    tags?: string[];
    title?: string;
  };

  type QuestionQueryRequest = {
    answer?: string;
    content?: string;
    current?: number;
    difficulty?: string;
    id?: string | number;
    notId?: string | number;
    pageSize?: number;
    questionBankId?: string | number;
    reviewStatus?: number;
    searchText?: string;
    sortField?: string;
    sortOrder?: string;
    tags?: string[];
    title?: string;
    userId?: string | number;
  };

  type QuestionReviewRequest = {
    id?: string | number;
    reviewMessage?: string;
    reviewStatus?: number;
  };

  type QuestionSubmitReviewRequest = {
    id?: string | number;
  };

  type QuestionResumeRecommendRequest = {
    resumeText?: string;
    size?: number;
  };

  type QuestionBankLeaderboardVO = {
    currentUserItem?: LeaderboardUserVO;
    description?: string;
    metricLabel?: string;
    questionBankId?: string | number;
    questionBankTitle?: string;
    rankingList?: LeaderboardUserVO[];
  };

  type QuestionUpdateRequest = {
    answer?: string;
    content?: string;
    difficulty?: string;
    id?: string | number;
    tags?: string[];
    title?: string;
  };

  type QuestionVO = {
    answer?: string;
    content?: string;
    createTime?: string;
    difficulty?: string;
    favourNum?: number;
    hasFavour?: boolean;
    id?: string | number;
    recommendReason?: string;
    questionStatus?: number;
    reviewMessage?: string;
    reviewStatus?: number;
    reviewTime?: string;
    reviewUserId?: string | number;
    tagList?: string[];
    title?: string;
    updateTime?: string;
    user?: UserVO;
    userId?: string | number;
  };

  type ResumeQuestionRecommendVO = {
    analysisSource?: string;
    analysisSummary?: string;
    extractedTags?: string[];
    jobDirection?: string;
    questionList?: QuestionVO[];
    recommendFocus?: string;
    resumeText?: string;
  };

  type SecurityAlertHandleRequest = {
    id?: string | number;
  };

  type SecurityAlertQueryRequest = {
    alertType?: string;
    current?: number;
    pageSize?: number;
    riskLevel?: string;
    searchText?: string;
    sortField?: string;
    sortOrder?: string;
    status?: number;
    userId?: string | number;
    userName?: string;
  };

  type SecurityAlert = {
    alertType?: string;
    createTime?: string;
    detail?: string;
    handleAction?: string;
    handleTime?: string;
    handlerUserId?: string | number;
    id?: string | number;
    ip?: string;
    isDelete?: number;
    reason?: string;
    riskLevel?: string;
    status?: number;
    updateTime?: string;
    userId?: string | number;
    userName?: string;
  };

  type uploadFileUsingPOSTParams = {
    biz?: string;
  };

  type User = {
    careerDirection?: string;
    city?: string;
    createTime?: string;
    editTime?: string;
    email?: string;
    id?: string | number;
    interestTags?: string | string[];
    profileVisibleFields?: string | string[];
    isDelete?: number;
    phone?: string;
    updateTime?: string;
    userAccount?: string;
    userAvatar?: string;
    userName?: string;
    userPassword?: string;
    userProfile?: string;
    userRole?: string;
  };

  type UserSendCodeRequest = {
    target?: string;
    type?: number;
    captcha?: string;
    captchaUuid?: string;
  };

  type UserBindRequest = {
    target?: string;
    code?: string;
  };

  type UserCodeLoginRequest = {
    target?: string;
    code?: string;
    type?: number;
  };

  type UserAddRequest = {
    careerDirection?: string;
    city?: string;
    interestTags?: string[];
    userAccount?: string;
    userAvatar?: string;
    userName?: string;
    userPassword?: string;
    userRole?: string;
  };

  type UserEditRequest = {
    email?: string;
    expertiseDirection?: string;
    grade?: string;
    phoneNumber?: string;
    userAvatar?: string;
    userName?: string;
    userProfile?: string;
    workExperience?: string;
  };

  type UserLoginRequest = {
    userAccount?: string;
    userPassword?: string;
    captcha?: string;
    captchaUuid?: string;
  };

  type UserQueryRequest = {
    careerDirection?: string;
    current?: number;
    id?: string | number;
    mpOpenId?: string;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
    userName?: string;
    userProfile?: string;
    userRole?: string;
  };

  type UserRegisterRequest = {
    checkPassword?: string;
    userAccount?: string;
    userPassword?: string;
  };

  type UserUpdateMyRequest = {
    careerDirection?: string;
    city?: string;
    interestTags?: string[];
    profileVisibleFields?: string[];
    userAccount?: string;
    userAvatar?: string;
    userName?: string;
    userProfile?: string;
    phone?: string;
    email?: string;
  };

  type UserInterestTagsMergeRequest = {
    interestTags?: string[];
  };

  type UserChangePasswordRequest = {
    oldPassword?: string;
    newPassword?: string;
    checkPassword?: string;
  };

  type QuestionFavourAddRequest = {
    questionId?: string | number;
  };

  type UserQuestionHistoryAddRequest = {
    questionId?: string | number;
    status?: number;
  };

  type UserUpdateRequest = {
    careerDirection?: string;
    city?: string;
    id?: string | number;
    interestTags?: string[];
    userAccount?: string;
    userAvatar?: string;
    userName?: string;
    userProfile?: string;
    userRole?: string;
  };

  type UserVO = {
    careerDirection?: string;
    city?: string;
    createTime?: string;
    hasFollowed?: boolean;
    id?: string | number;
    interestTagList?: string[];
    profileVisibleFieldList?: string[];
    userAvatar?: string;
    userName?: string;
    userProfile?: string;
    userRole?: string;
  };

  type UserProfileVO = {
    activeDays?: number;
    achievementList?: Record<string, any>[];
    averageStudyDurationSeconds?: number;
    approvedQuestionBankCount?: number;
    approvedQuestionCount?: number;
    currentStreak?: number;
    dailyTarget?: number;
    favourCount?: number;
    followerCount?: number;
    followingCount?: number;
    goalCompletedToday?: boolean;
    hasFollowed?: boolean;
    masteredQuestionCount?: number;
    profileVisibleFieldList?: string[];
    questionHistoryRecordList?: Record<string, any>[];
    recommendedDifficulty?: string;
    recentActivityList?: UserActivityVO[];
    studySessionCount?: number;
    todayCount?: number;
    todayStudyDurationSeconds?: number;
    totalQuestionCount?: number;
    totalStudyDurationSeconds?: number;
    user?: UserVO;
  };

  type UserActivityVO = {
    activityTime?: string;
    badge?: string;
    description?: string;
    targetId?: string | number;
    targetUrl?: string;
    title?: string;
    type?: string;
  };

  type UserQuestionNoteSaveRequest = {
    content?: string;
    questionId?: string | number;
  };

  type UserQuestionNoteQueryRequest = {
    current?: number;
    pageSize?: number;
  };

  type UserQuestionNoteVO = {
    content?: string;
    createTime?: string;
    id?: string | number;
    question?: QuestionVO;
    questionId?: string | number;
    updateTime?: string;
    userId?: string | number;
  };

  type PageUserQuestionNoteVO_ = {
    countId?: string;
    current?: number;
    maxLimit?: number;
    optimizeCountSql?: boolean;
    orders?: OrderItem[];
    pages?: number;
    records?: UserQuestionNoteVO[];
    searchCount?: boolean;
    size?: number;
    total?: number;
  };

  type BaseResponseUserQuestionNoteVO_ = {
    code?: number;
    data?: UserQuestionNoteVO;
    message?: string;
  };

  type BaseResponsePageUserQuestionNoteVO_ = {
    code?: number;
    data?: PageUserQuestionNoteVO_;
    message?: string;
  };

  type QuestionRecommendClickRequest = {
    questionId?: string | number;
    source?: string;
  };
  type UserFollowRequest = {
    followUserId?: string | number;
  };

  type UserFollowQueryRequest = {
    current?: number;
    pageSize?: number;
    sortField?: string;
    sortOrder?: string;
    userId?: string | number;
  };
  type UserQuestionHistoryVO = {
    createTime?: string;
    id?: string | number;
    question?: QuestionVO;
    questionId?: string | number;
    status?: number;
    updateTime?: string;
    userId?: string | number;
  };

  type NotificationVO = {
    id?: string | number;
    userId?: string | number;
    title?: string;
    content?: string;
    type?: string;
    status?: number;
    targetId?: string | number;
    targetUrl?: string;
    createTime?: string;
    updateTime?: string;
  };

  type NotificationAddRequest = {
    userId?: string | number;
    title?: string;
    content?: string;
    type?: string;
    targetId?: string | number;
  };

  type NotificationQueryRequest = {
    current?: number;
    pageSize?: number;
    id?: string | number;
    userId?: string | number;
    title?: string;
    content?: string;
    type?: string;
    status?: number;
    targetId?: string | number;
    sortField?: string;
    sortOrder?: string;
  };
}
