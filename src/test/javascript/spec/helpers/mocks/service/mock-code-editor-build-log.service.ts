import { of } from 'rxjs';
import { BuildLogEntry } from 'app/buildagent/shared/entities/build-log.model';
import { IBuildLogService } from 'app/programming/shared/services/build-log.service';

export class MockCodeEditorBuildLogService implements IBuildLogService {
    getBuildLogs = () => of([] as BuildLogEntry[]);
    getTestRepositoryBuildLogs = (participationId: number) => of([] as BuildLogEntry[]);
}
