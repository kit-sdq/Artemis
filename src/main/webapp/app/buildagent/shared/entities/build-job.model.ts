import { StringBaseEntity } from 'app/shared/model/base-entity';
import { RepositoryInfo, TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { JobTimingInfo } from 'app/buildagent/shared/entities/job-timing-info.model';
import { BuildConfig } from 'app/buildagent/shared/entities/build-config.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { BuildAgent } from 'app/buildagent/shared/entities/build-agent.model';

export class BuildJob implements StringBaseEntity {
    public id?: string;
    public name?: string;
    public buildAgent?: BuildAgent;
    public participationId?: number;
    public courseId?: number;
    public exerciseId?: number;
    public retryCount?: number;
    public priority?: number;
    public status?: string;
    public repositoryInfo?: RepositoryInfo;
    public jobTimingInfo?: JobTimingInfo;
    public buildConfig?: BuildConfig;
    public submissionResult?: Result;
}

export class FinishedBuildJob implements StringBaseEntity {
    public id?: string;
    public name?: string;
    public buildAgentAddress?: string;
    public participationId?: number;
    public courseId?: number;
    public exerciseId?: number;
    public retryCount?: number;
    public priority?: number;
    public status?: string;
    public triggeredByPushTo?: TriggeredByPushTo;
    public repositoryName?: string;
    public repositoryType?: string;
    public buildSubmissionDate?: dayjs.Dayjs;
    public buildStartDate?: dayjs.Dayjs;
    public buildCompletionDate?: dayjs.Dayjs;
    public buildDuration?: string;
    public commitHash?: string;
    public submissionResult?: Result;
}

export class BuildJobStatistics {
    public totalBuilds: number = 0;
    public successfulBuilds: number = 0;
    public failedBuilds: number = 0;
    public cancelledBuilds: number = 0;
    public timeOutBuilds: number = 0;
    public missingBuilds: number = 0;
}

export enum SpanType {
    DAY = 1,
    WEEK = 7,
    MONTH = 30,
}
